// The page's events, the one place they are named; the vocabulary is TrendingAI's docs/telemetry-vocabulary.md.
import { analytics } from "tinyui-native";
import type { Plan } from "./api.ts";

export const checkoutStep = (step: "plan_selected" | "opened", plan: Plan) => analytics.track("checkout_step", { step, plan });

/** Same event the App's native requests send when an API call fails; status is -1 without a response. */
export const apiFailed = (endpoint: string, status: number) => analytics.track("api_failed", { endpoint, status });
