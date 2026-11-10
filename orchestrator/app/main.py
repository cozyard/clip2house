import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel

from .config import settings
from .consistency import ConsistencyChecker
from .items import generate_item
from .ledger import LedgerClient
from .personas import load_personas
from .providers import MockProvider, NvidiaProvider, Provider, ProviderRegistry
from .scheduler import QuietWindowScheduler
from .turns import TurnPipeline

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)


def _build_providers() -> list[Provider]:
    if settings.provider == "nvidia":
        if not settings.nvidia_api_key:
            raise RuntimeError("C2H_PROVIDER=nvidia이면 C2H_NVIDIA_API_KEY가 필요합니다")
        return [NvidiaProvider(settings.nvidia_api_key, settings.nvidia_model, settings.nvidia_base_url)]
    if settings.provider == "mock":
        return [MockProvider()]
    raise RuntimeError(f"지원하지 않는 Provider입니다: {settings.provider}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    personas = {p.id: p for p in load_personas(settings.persona_count, settings.persona_seed)}
    ledger = LedgerClient(settings.ledger_base_url, settings.internal_api_secret)
    providers = _build_providers()
    registry = ProviderRegistry(providers, offline_seconds=settings.offline_minutes * 60)
    pipeline = TurnPipeline(ledger, registry, personas, settings.retry_limit,
                            llm_classifier=settings.classifier_llm_enabled)
    scheduler = QuietWindowScheduler(pipeline.run_turn, personas,
                                     settings.quiet_window_min_s, settings.quiet_window_max_s)

    consistency = ConsistencyChecker(ledger, personas, settings.consistency_interval_s)

    app.state.ledger = ledger
    app.state.registry = registry
    app.state.pipeline = pipeline
    app.state.scheduler = scheduler
    app.state.personas = personas

    asyncio.create_task(_sync_personas_with_retry(ledger, personas))
    consistency.start()

    yield

    await consistency.stop()
    await scheduler.shutdown()
    await ledger.close()
    for provider in providers:
        await provider.close()


async def _sync_personas_with_retry(ledger: LedgerClient, personas: dict) -> None:
    payload = [{"id": p.id, "nickname": p.nickname, "community": p.region} for p in personas.values()]
    for delay in (0, 3, 10, 30, 60):
        await asyncio.sleep(delay)
        try:
            await ledger.sync_personas(payload)
            logger.info("페르소나 %d명 동기화 완료", len(payload))
            return
        except Exception as exc:
            logger.warning("페르소나 동기화 실패(재시도 예정): %s", exc)
    logger.error("페르소나 동기화를 포기합니다. 장부의 Faucet은 기존 페르소나로만 돕니다.")


app = FastAPI(title="clip2house-orchestrator", lifespan=lifespan)


@app.middleware("http")
async def internal_secret_guard(request: Request, call_next):
    if request.url.path.startswith("/internal/"):
        if request.headers.get("X-Internal-Secret") != settings.internal_api_secret:
            return Response(status_code=404)
    return await call_next(request)


class TurnNotify(BaseModel):
    negotiationId: int
    personaId: int


@app.post("/internal/turns")
async def notify_turn(notify: TurnNotify, request: Request):
    registry: ProviderRegistry = request.app.state.registry
    if registry.is_offline(notify.personaId):
        return {"scheduled": False, "reason": "persona-offline"}
    request.app.state.scheduler.notify(notify.negotiationId, notify.personaId)
    return {"scheduled": True}


class GenerateRequest(BaseModel):
    targetLog10Value: float
    anchors: list[dict]
    existingNames: list[str] = []


@app.post("/internal/items/generate")
async def generate(request_body: GenerateRequest, request: Request):
    personas = request.app.state.personas
    registry: ProviderRegistry = request.app.state.registry

    provider = registry.provider_for(next(iter(personas)))
    generated = await generate_item(provider, settings, request_body.targetLog10Value,
                                    request_body.anchors, request_body.existingNames,
                                    settings.retry_limit)
    if generated is None:
        raise HTTPException(status_code=503, detail="아이템 생성 실패")
    return generated


@app.get("/healthz")
async def healthz():
    return {"status": "ok"}


@app.get("/metrics")
async def metrics():
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


def run() -> None:
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port)


if __name__ == "__main__":
    run()
