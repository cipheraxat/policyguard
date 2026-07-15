from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from policyguard.api.routes import router
from policyguard.config import get_settings
from policyguard.deps import bootstrap

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    logger.info("Starting PolicyGuard profile=%s", settings.profile)
    if not settings.reviewers_allowed_ids:
        logger.warning(
            "POLICYGUARD_REVIEWERS_ALLOWED_IDS is empty — reviewer auth is in DEV mode"
        )
    bootstrap(settings)
    yield


app = FastAPI(title="PolicyGuard", version="1.0.0", lifespan=lifespan)
app.include_router(router)


def run() -> None:
    import uvicorn

    uvicorn.run("policyguard.main:app", host="0.0.0.0", port=8080, reload=False)


if __name__ == "__main__":
    run()
