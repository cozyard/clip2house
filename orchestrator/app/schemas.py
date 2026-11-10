from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


Action = Literal["CONTINUE", "OFFER", "ACCEPT", "DECLINE"]


class TurnAction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    action: Action

    messages: list[str] = Field(min_length=1, max_length=3)

    offer_holding_id: int | None = None

    accept_offer_id: int | None = None


TURN_ACTION_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["action", "messages"],
    "properties": {
        "action": {"type": "string", "enum": ["CONTINUE", "OFFER", "ACCEPT", "DECLINE"]},
        "messages": {
            "type": "array",
            "minItems": 1,
            "maxItems": 3,
            "items": {"type": "string", "maxLength": 500},
        },
        "offer_holding_id": {"type": ["integer", "null"]},
        "accept_offer_id": {"type": ["integer", "null"]},
    },
}


class GeneratedItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=60)
    description: str = Field(min_length=1, max_length=300)
    proposed_value: int = Field(gt=0)
    cited_anchor_name: str


def item_json_schema(anchor_names: list[str]) -> dict:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["name", "description", "proposed_value", "cited_anchor_name"],
        "properties": {
            "name": {"type": "string", "maxLength": 60},
            "description": {"type": "string", "maxLength": 300},
            "proposed_value": {"type": "integer", "minimum": 1},
            "cited_anchor_name": {"type": "string", "enum": anchor_names},
        },
    }
