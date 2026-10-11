// TrendingAI's backend as the subscription page uses it; requests that need the user go through the host's `app` channel.
import { http } from "tinyui-native";

export const BASE = "https://api.trendingai.cn";

/** Carries the user's session and install id (TrendingAI's tinyui/TrendingTinyUI.kt). */
const app = http.client("app");

export type Plan = "annual" | "monthly";

/** `/api/billing/prices`: priced by the visitor's region; `formatted` carries the currency. */
export interface Prices {
    annual?: { formatted: string } | null;
    monthly?: { formatted: string } | null;
    /** what yearly saves over twelve months; null when it saves nothing */
    annual_savings_percent?: number | null;
}

/** Both plans priced, or the page shows no number at all: half a price, or a wrong one, costs more trust than none. */
export const available = (p: Prices | undefined) => !!p?.annual && !!p.monthly;

export async function prices(): Promise<Prices> {
    return (await app.get<Prices>(`${BASE}/api/billing/prices`)).body;
}

/** Creates the Paddle transaction; its checkout URL. */
export async function checkout(plan: Plan): Promise<string> {
    return (await app.post<{ url: string }>(`${BASE}/api/billing/checkout`, { plan })).body.url;
}

/** Whether the signed-in user holds Pro. */
export async function isPro(): Promise<boolean> {
    return (await app.get<{ pro?: boolean }>(`${BASE}/api/me`)).body.pro === true;
}

/** Copy in both languages; a missing one falls back as the server intends (zh → en). */
export interface Localized {
    zh?: string | null;
    en?: string | null;
}

export interface Paywall {
    title?: Localized | null;
    subtitle?: Localized | null;
    benefits?: { icon?: string | null; text?: Localized | null }[];
    cta?: { subscribe?: Localized | null; sign_in?: Localized | null; view_price?: Localized | null } | null;
    refund_note?: Localized | null;
    already_pro?: Localized | null;
    checkout_failed?: Localized | null;
}

/** `pro_paywall` of `/api/app-config`: the server's word on every string of the page (TrendingAI CLAUDE.md). */
export async function paywall(): Promise<Paywall | null> {
    return (await app.get<{ pro_paywall?: Paywall | null }>(`${BASE}/api/app-config`)).body.pro_paywall ?? null;
}

export function pick(text: Localized | null | undefined, locale: string): string | undefined {
    if (!text) return undefined;
    return (locale.startsWith("zh") ? text.zh ?? text.en : text.en) ?? undefined;
}
