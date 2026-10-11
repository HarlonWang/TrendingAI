import { Box, Button, Column, effect, For, HostError, Icon, Loading, observable, pageVisible, RadioButton, resource, Row, Show, signal, Spacer, Text, untrack } from "tinyui-core";
import { cached, events, i18n, linking, session, storage, ui } from "tinyui-native";
import bolt from "@material-symbols/svg-400/outlined/bolt.svg";
import brush from "@material-symbols/svg-400/outlined/brush.svg";
import checkCircle from "@material-symbols/svg-400/outlined/check_circle.svg";
import mic from "@material-symbols/svg-400/outlined/mic.svg";
import starShine from "@material-symbols/svg-400/outlined/star_shine.svg";
import travelExplore from "@material-symbols/svg-400/outlined/travel_explore.svg";
import { apiFailed, checkoutStep } from "../analytics.ts";
import { available, checkout, isPro, paywall, pick, prices, type Plan, type Prices } from "../api.ts";

/** Keys of the server's benefit rows (lib/pro-paywall.js); an unknown key gets the generic check, so new rows need no release. */
const BENEFIT_ICONS: Record<string, string> = { quota: bolt, models: starShine, voice: mic, image_generation: brush, search: travelExplore };

/** Webhook-backed entitlement lands after the return from checkout; asked again at these delays (TrendingAI ProCheckout). */
const PRO_RETRIES_MS = [0, 3000, 8000, 15000];

/**
 * The paywall. Every string is the server's `pro_paywall` first, the package's own as the fallback; no price is ever
 * hard-coded; GitHub Sponsors is never offered here (TrendingAI CLAUDE.md, SubscriptionScreen).
 */
export default function Subscription() {
    const t = i18n.t;
    const [remote] = cached("paywall.v1", paywall);
    const [priced, priceActions] = resource(() => prices().catch((e: unknown) => { failed("billing/prices", e); return {} as Prices; }));
    const state = observable({ plan: "annual" as Plan, checkingOut: false });
    const [pro, setPro] = signal(storage.get<boolean>("pro.v1") ?? false);
    let awaitingPurchase = false;
    /** Bumped by every refresh and by logout: a late answer to an older one is dropped. */
    let generation = 0;

    const text = (remoteText: string | undefined, key: Parameters<typeof t>[0]) => remoteText ?? t(key);
    const copy = () => remote() ?? null;
    const loc = () => i18n.locale();

    function refreshPro() {
        const mine = ++generation;
        // each ask is timed from the first, and a failed one does not end the series
        for (const at of awaitingPurchase ? PRO_RETRIES_MS : [0]) {
            setTimeout(() => {
                if (mine !== generation) return;
                isPro().then((p) => {
                    if (mine !== generation) return;
                    setPro(p);
                    storage.set("pro.v1", p);
                    if (p) { awaitingPurchase = false; generation++; }
                }, (e: unknown) => failed("me", e));
            }, at);
        }
    }
    effect(() => {
        const loggedIn = session.state().loggedIn;
        if (!loggedIn) {
            untrack(() => { generation++; setPro(false); storage.set("pro.v1", false); });
            return;
        }
        if (pageVisible()) untrack(refreshPro);
    });

    function selectPlan(plan: Plan) {
        if (state.plan === plan) return;
        checkoutStep("plan_selected", plan);
        state.plan = plan;
    }

    async function onCta() {
        // buying needs a session: the subscription hangs on app_users.user_id
        if (!session.state().loggedIn) { void session.signIn("paywall"); return; }
        if (state.checkingOut) return;
        state.checkingOut = true;
        const plan = state.plan;
        try {
            await linking.openUrl(await checkout(plan));
            checkoutStep("opened", plan);
            // the App starts reconciling on its return to the foreground; this page asks again when it is visible
            events.emit("trendingai.checkout.opened", { plan });
            awaitingPurchase = true;
        } catch (e) {
            failed("billing/checkout", e);
            void ui.toast(text(pick(copy()?.checkout_failed, loc()), "subscription.checkoutFailed"));
        } finally {
            state.checkingOut = false;
        }
    }

    const benefits = () => (copy()?.benefits ?? []).flatMap((row) => {
        const line = pick(row.text, loc());
        return line ? [{ icon: BENEFIT_ICONS[row.icon ?? ""] ?? checkCircle, text: line }] : [];
    });
    const ctaText = () => {
        const cta = copy()?.cta;
        if (!session.state().loggedIn) return text(pick(cta?.sign_in, loc()), "subscription.cta.signIn");
        return available(priced()) ? text(pick(cta?.subscribe, loc()), "subscription.cta.subscribe") : text(pick(cta?.view_price, loc()), "subscription.cta.viewPrice");
    };
    const badge = () => {
        const percent = priced()?.annual_savings_percent;
        return percent == null ? undefined : t("subscription.savings", { percent: `${percent}%` });
    };

    return (
        <Column width="fill" height="fill" scroll paddingHorizontal={16}>
            <Spacer height={8} />
            <Text text={text(pick(copy()?.title, loc()), "subscription.title")} style="headlineMedium" align="center" width="fill" />
            <Spacer height={8} />
            <Text text={text(pick(copy()?.subtitle, loc()), "subscription.intro")} style="bodyMedium" color="onSurfaceVariant" align="center" width="fill" />

            <Spacer height={24} />
            <Show when={benefits().length > 0} fallback={() => <Text text={t("subscription.benefitsFallback")} style="bodyLarge" align="center" width="fill" />}>
                {() => (
                    <Column gap={16}>
                        <For each={benefits()} key={(b) => b.text}>
                            {(b) => (
                                <Row align="center" gap={16}>
                                    <Icon icon={b().icon} tint="primary" />
                                    <Text text={b().text} style="bodyLarge" />
                                </Row>
                            )}
                        </For>
                    </Column>
                )}
            </Show>

            <Spacer height={24} />
            <Show when={!priceActions.loading()} fallback={() => <Box width="fill" paddingVertical={24} align="center"><Loading /></Box>}>
                {() => (
                    <Column gap={8}>
                        <PlanCard title={t("subscription.plan.annual")} unit={t("subscription.plan.annualUnit")} price={priced()?.annual?.formatted}
                            badge={badge()} selected={state.plan === "annual"} onClick={() => selectPlan("annual")} />
                        <PlanCard title={t("subscription.plan.monthly")} unit={t("subscription.plan.monthlyUnit")} price={priced()?.monthly?.formatted}
                            badge={undefined} selected={state.plan === "monthly"} onClick={() => selectPlan("monthly")} />
                    </Column>
                )}
            </Show>

            <Spacer height={16} />
            {/* a Pro user reaching this page (deep link, back stack) is not sold to */}
            <Show when={pro()} fallback={() => (
                <Button width="fill" enabled={!state.checkingOut} onClick={() => void onCta()} text={ctaText()}>
                    <Show when={state.checkingOut}>{() => <Loading color="onPrimary" />}</Show>
                </Button>
            )}>
                {() => <Text text={text(pick(copy()?.already_pro, loc()), "subscription.alreadyPro")} style="bodyMedium" color="onSurfaceVariant" />}
            </Show>

            <Spacer height={12} />
            <Text text={text(pick(copy()?.refund_note, loc()), "subscription.refundNote")} style="bodySmall" color="onSurfaceVariant" />
            <Spacer height={24} />
        </Column>
    );
}

function failed(endpoint: string, e: unknown) {
    apiFailed(endpoint, e instanceof HostError && e.status !== undefined ? e.status : -1);
}

interface PlanCardProps {
    title: string;
    unit: string;
    price: string | undefined;
    badge: string | undefined;
    selected: boolean;
    onClick: () => void;
}

/** Annual is preselected and carries the savings badge — all there is of "annual first"; monthly sits beside it, never folded. */
function PlanCard(p: PlanCardProps) {
    return (
        <Row
            width="fill"
            align="center"
            padding={12}
            cornerRadius={12}
            background={p.selected ? "secondaryContainer" : "surfaceContainer"}
            borderWidth={p.selected ? 2 : 0}
            borderColor="primary"
            role="radio"
            selected={p.selected}
            onClick={() => p.onClick()}
        >
            <RadioButton selected={p.selected} />
            <Column weight={1}>
                <Row align="center" gap={8}>
                    <Text text={p.title} style="titleSmall" />
                    <Show when={p.badge}>
                        {() => (
                            <Box background="primary" cornerRadius={4} paddingHorizontal={6} paddingVertical={2}>
                                <Text text={p.badge ?? ""} style="labelSmall" color="onPrimary" />
                            </Box>
                        )}
                    </Show>
                </Row>
                {/* no price, no line: better no number than one that may be wrong */}
                <Show when={p.price}>{() => <Text text={`${p.price} · ${p.unit}`} style="bodyMedium" color="onSurfaceVariant" />}</Show>
            </Column>
        </Row>
    );
}
