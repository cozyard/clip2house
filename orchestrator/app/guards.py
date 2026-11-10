import re


_VALUE_LEAK = [
    re.compile(r"\d{1,3}(,\d{3})+\s*원"),
    re.compile(r"\d{4,}\s*원"),
    re.compile(r"\d+\s*(억|만)\s*원"),
    re.compile(r"(가치\s*)?비율\s*[은는이가:]?\s*\d"),
    re.compile(r"\b0?\.\d{1,4}\s*(배|비율)"),
    re.compile(r"참조\s*가치|기준\s*가치"),
]

_SYSTEM_SPEAK = [
    re.compile(r"(?i)system\s*prompt|instruction|assistant|language\s*model"),
    re.compile(r"시스템\s*(프롬프트|메시지|지시)"),
    re.compile(r"(저는|나는)\s*(AI|인공지능|모델|챗봇|어시스턴트)"),
    re.compile(r"(?i)\bAI\s*(모델|입니다|이에요|예요)"),
    re.compile(r"프롬프트|페르소나\s*(설정|카드)"),
]

_RAW_TOKEN = [
    re.compile(r"<\|.*?\|>"),
    re.compile(r"\{\{.*?\}\}"),
    re.compile(r"(?i)\[/?INST\]|<\/?(tool_use|function|system)>"),
    re.compile(r'"action"\s*:'),
]


def find_violation(text: str) -> str | None:
    for pattern in _VALUE_LEAK:
        if pattern.search(text):
            return "VALUE_LEAK"
    for pattern in _SYSTEM_SPEAK:
        if pattern.search(text):
            return "SYSTEM_SPEAK"
    for pattern in _RAW_TOKEN:
        if pattern.search(text):
            return "RAW_TOKEN"
    return None


def check_messages(messages: list[str]) -> str | None:
    for message in messages:
        violation = find_violation(message)
        if violation:
            return violation
    return None
