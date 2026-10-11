import { Column, For, Show, Spacer, Text } from "tinyui-core";
import { i18n } from "tinyui-native";

type Key = Parameters<typeof i18n.t>[0];

/** Source names are brands, the same in every language. */
interface Source {
    name: string;
    source: Key;
    scope?: Key;
}

/**
 * Cadence goes into `source` as "how often" only: the exact time is given by each list's footer in the reader's
 * time zone (TrendingAI SourceMetaFooter); a cron time written here would need converting and go stale.
 */
const SOURCES: Source[] = [
    { name: "GitHub", source: "dataSources.github.source", scope: "dataSources.github.scope" },
    { name: "Hacker News", source: "dataSources.hn.source", scope: "dataSources.hn.scope" },
    { name: "Product Hunt", source: "dataSources.ph.source", scope: "dataSources.ph.scope" },
    // no scope for Picks: it could only describe the internal scoring and demotion rules
    { name: "Picks", source: "dataSources.picks.source" },
];

/** Cadence and coverage of the three sources and Picks; the one place in the app that states them. Nothing here is clickable. */
export default function DataSources() {
    const t = i18n.t;
    return (
        <Column width="fill" height="fill" scroll paddingHorizontal={16} gap={12}>
            <Text text={t("dataSources.intro")} style="bodyMedium" color="onSurfaceVariant" paddingVertical={16} />
            <For each={SOURCES} key={(s) => s.name}>
                {(s) => (
                    <Column width="fill" padding={20} gap={10} cornerRadius={24} background="surfaceContainer">
                        <Text text={s().name} style="titleMedium" />
                        <Text text={t(s().source)} style="bodyMedium" color="onSurfaceVariant" />
                        <Show when={s().scope}>{() => <Text text={t(s().scope!)} style="bodyMedium" color="onSurfaceVariant" />}</Show>
                    </Column>
                )}
            </For>
            <Spacer height={12} />
        </Column>
    );
}
