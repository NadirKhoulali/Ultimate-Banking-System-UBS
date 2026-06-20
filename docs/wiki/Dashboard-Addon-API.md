# Dashboard Addon API

Dashboard API baseline: `2.0.0`

UBS exposes a component-driven web dashboard host for addon mods. The goal is that an addon can appear in the UBS admin dashboard, add its own left-nav panels, render UBS-styled pages, expose live data, and run server actions without hosting a second web server or writing dashboard HTML/CSS.

Target audience: mod developers and agents implementing addon dashboards.

## What UBS Provides

UBS provides:

- automatic detection of loaded mods that depend on `ultimatebankingsystem`
- a dashboard switcher for registered addon dashboards
- left-nav panels owned by each dashboard
- component pages rendered with the same UBS spacing, cards, panels, tables, charts, forms, and buttons as the built-in dashboard
- addon API routes under `/api/webadmin/addons/...` for data, actions, and custom route handlers
- optional iframe/resource serving for advanced cases

Prefer component pages. Use iframe/custom resources only when a component cannot represent the UI.

## Dependency

Add UBS as a required dependency in your `META-INF/neoforge.mods.toml`:

```toml
[[dependencies.youraddon]]
modId="ultimatebankingsystem"
mandatory=true
versionRange="[2.0.0,)"
ordering="AFTER"
side="BOTH"
```

UBS detects loaded mods that depend on `ultimatebankingsystem`. If your mod depends on UBS but does not register a dashboard, it can still appear as disabled metadata in the dashboard switcher. Register a dashboard to make it usable.

## Entry Points

Use the dashboard API:

```java
import net.austizz.ultimatebankingsystem.api.dashboard.UltimateBankingDashboardApi;
import net.austizz.ultimatebankingsystem.api.dashboard.UltimateBankingDashboardApiProvider;

UltimateBankingDashboardApi dashboardApi = UltimateBankingDashboardApiProvider.get();
String version = dashboardApi.getDashboardApiVersion(); // 2.0.0
```

Register through either entry point:

```java
DashboardRegistrationResult result =
        UltimateBankingDashboardApiProvider.registry().registerDashboard(dashboard);
```

or:

```java
DashboardRegistrationResult result = dashboardApi.registerDashboard(dashboard);
```

Register during common setup or another point after your mod has initialized. Keep registration deterministic; registering the same `modId` again replaces the previous dashboard definition.

## IDs And Validation

Dashboard, panel, page, component, widget, and action IDs must be lowercase identifiers matching:

```text
[a-z0-9_][a-z0-9_.-]{1,63}
```

Practical rules:

- use lowercase ids such as `overview`, `market-feed`, `auction_kpis`
- do not use display names as IDs
- do not duplicate page IDs, panel IDs, component IDs, widget IDs, or action IDs inside their scope
- every dashboard needs a non-empty title
- every panel and page needs a non-empty title

## Mental Model

Dashboard structure:

```text
DashboardDefinition
  panels: left-nav entries and optional legacy/widget route holders
  pages: component-rendered pages
    components: actual UI tree
```

For the modern component UI:

- `DashboardPanelDefinition` controls left-nav visibility.
- `DashboardPageDefinition` controls the rendered page for a matching panel ID.
- A page should have the same ID as its nav panel.
- The page `dataUrl` returns one JSON snapshot.
- Components use `dataPath` to read parts of that snapshot.

For addon data/actions without hosting a web server:

- create a panel widget with `dataProvider`, `actionHandler`, or `routeHandler`
- UBS exposes that widget through `/api/webadmin/addons/...`
- point component `dataUrl` or form endpoints at those UBS-hosted addon routes

## Left Nav Panels

To add a panel to the left nav, add a `DashboardPanelDefinition` to your dashboard:

```java
DashboardPanelDefinition overviewPanel = DashboardPanelDefinition.builder("overview", "Overview")
        .subtitle("Auction house operations")
        .order(0)
        .build();
```

Then add a page with the same ID:

```java
DashboardPageDefinition overviewPage = DashboardPageDefinition.builder("overview", "Overview")
        .subtitle("Auction metrics and live activity")
        .routePattern("#/d/youraddon/overview")
        .dataUrl("/api/webadmin/addons/youraddon/routes/overview/page-data/snapshot")
        .component(/* components */)
        .build();
```

Rules:

- `panel.id()` is the left-nav route ID.
- `page.id()` should match `panel.id()` for normal nav pages.
- `panel.order()` controls left-nav ordering.
- `dashboard.order()` controls dashboard switcher ordering.
- `panel.nativeRoute(...)` is for UBS internal pages; addons should usually leave it blank.

## Dashboard Registration Example

This example adds one left-nav panel, one component page, and one hidden route widget that supplies the page snapshot through UBS.

```java
import com.google.gson.JsonObject;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponentDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponents;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardLayoutDefaults;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPageDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPanelDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardRegistrationResult;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardResponse;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardWidgetDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardWidgetType;
import net.austizz.ultimatebankingsystem.api.dashboard.UltimateBankingDashboardApiProvider;

import java.util.List;
import java.util.Map;

DashboardWidgetDefinition pageDataRoute = DashboardWidgetDefinition.builder("page-data", DashboardWidgetType.OUTPUT)
        .routeHandler((context, routePath, body) -> DashboardResponse.ok(Map.of(
                "ok", true,
                "metrics", Map.of(
                        "openAuctions", 14,
                        "bidVolume", 12340.50,
                        "failedDeliveries", 2,
                        "activeBidders", 8
                ),
                "topItems", List.of(
                        Map.of("itemName", "Diamond", "bids", 22, "revenue", 4100.00),
                        Map.of("itemName", "Netherite Ingot", "bids", 7, "revenue", 8800.00)
                ),
                "history", List.of(
                        Map.of("time", "10:00", "deposits", 1200, "reserves", 800),
                        Map.of("time", "11:00", "deposits", 1600, "reserves", 900)
                ),
                "warnings", List.of(
                        Map.of("tone", "warn", "text", "2 deliveries need admin review.")
                )
        )))
        .build();

DashboardDefinition dashboard = DashboardDefinition.builder("youraddon", "Your Addon")
        .subtitle("Auction house operations")
        .icon("AH")
        .order(20)
        .defaults(DashboardLayoutDefaults.ubs())
        .panel(DashboardPanelDefinition.builder("overview", "Overview")
                .subtitle("Auction activity")
                .order(0)
                .widget(pageDataRoute)
                .build())
        .page(DashboardPageDefinition.builder("overview", "Overview")
                .subtitle("Auction metrics and live activity")
                .routePattern("#/d/youraddon/overview")
                .dataUrl("/api/webadmin/addons/youraddon/routes/overview/page-data/snapshot")
                .component(DashboardComponents.kpiGroup("auction-kpis", "metrics", List.of(
                        Map.of("label", "Open Auctions", "path", "openAuctions", "format", "number"),
                        Map.of("label", "Bid Volume", "path", "bidVolume", "format", "money"),
                        Map.of("label", "Failed Deliveries", "path", "failedDeliveries", "format", "number"),
                        Map.of("label", "Active Bidders", "path", "activeBidders", "format", "number")
                )).option("placement", "top").build())
                .component(DashboardComponents.panel("warning-panel", "Warnings")
                        .child(DashboardComponentDefinition.builder("warnings", DashboardComponents.ALERT_LIST)
                                .dataPath("warnings")
                                .build())
                        .build())
                .component(DashboardComponents.twoColumn("overview-split")
                        .child(DashboardComponents.table("top-items", "Top Items", "topItems", List.of(
                                Map.of("label", "Item", "path", "itemName"),
                                Map.of("label", "Bids", "path", "bids", "format", "number"),
                                Map.of("label", "Revenue", "path", "revenue", "format", "money")
                        )).build())
                        .child(DashboardComponents.chartPanel("bid-trend", "Bid Trend", DashboardComponents.LINE_CHART, "history").build())
                        .build())
                .build())
        .build();

DashboardRegistrationResult result = UltimateBankingDashboardApiProvider.registry().registerDashboard(dashboard);
if (!result.success()) {
    throw new IllegalStateException(result.message());
}
```

## Data URLs And Addon Routes

Component pages need a `dataUrl`. UBS does not require that URL to be served by your mod. You can route it through UBS:

```text
GET /api/webadmin/addons/{modId}/widgets/{panelId}/{widgetId}
POST /api/webadmin/addons/{modId}/actions/{panelId}/{widgetId}
GET|POST /api/webadmin/addons/{modId}/routes/{panelId}/{widgetId}/{routePath}
```

Use cases:

- `dataProvider(...)`: simple read-only widget data
- `actionHandler(...)`: simple POST action endpoint
- `routeHandler(...)`: flexible endpoint for component page snapshots, detail pages, or action APIs

Recommended component-page pattern:

1. Add a panel to create the left-nav entry.
2. Add a route widget to that panel, usually named `page-data` or `routes`.
3. Set the page `dataUrl` to `/api/webadmin/addons/{modId}/routes/{panelId}/{widgetId}/snapshot`.
4. Return one JSON object from the route handler.

The `DashboardRequestContext` gives handlers:

- `server()`
- `sessionId()`
- `remoteAddress()`
- `modId()`
- `panelId()`
- `widgetId()`
- `method()`
- `routePath()`
- `queryParameters()`

## Page Snapshot Shape

Each page should return one stable snapshot object. Components bind into it with `dataPath`.

Example:

```json
{
  "ok": true,
  "metrics": {
    "openAuctions": 14,
    "bidVolume": 12340.5
  },
  "topItems": [
    {"itemName": "Diamond", "bids": 22, "revenue": 4100.0}
  ],
  "history": [
    {"time": "10:00", "deposits": 1200, "reserves": 800}
  ],
  "warnings": [
    {"tone": "warn", "text": "2 deliveries need admin review."}
  ]
}
```

Then:

- `dataPath("metrics")` gives a KPI group the `metrics` object.
- `dataPath("topItems")` gives a table its rows.
- `dataPath("history")` gives a chart its rows.
- `dataPath("warnings")` gives an alert list its rows.

`dataPath` supports dot paths such as `charts.economyHistory`.

## Layout Defaults

Use UBS defaults unless you have a strong reason not to:

```java
.defaults(DashboardLayoutDefaults.ubs())
```

`DashboardLayoutDefaults.ubs()` currently sets:

| Key | Default | Meaning |
| --- | ---: | --- |
| `density` | `comfortable` | Default component density. |
| `sectionGap` | `12` | Gap between dashboard sections. |
| `panelPadding` | `16` | Standard panel padding. |
| `kpiColumns` | `6` | Normal KPI grid columns. |
| `compactKpiColumns` | `4` | Compact KPI grid columns. |
| `twoColumnMinWidth` | `760` | Breakpoint hint for two-column layouts. |
| `tableMaxHeight` | `420` | Default scroll height for tables. |
| `chartHeight` | `250` | Default chart height. |

You can override defaults:

```java
.defaults(DashboardLayoutDefaults.builder()
        .value("density", "comfortable")
        .value("sectionGap", 12)
        .value("panelPadding", 16)
        .value("kpiColumns", 6)
        .value("compactKpiColumns", 4)
        .value("twoColumnMinWidth", 760)
        .value("tableMaxHeight", 420)
        .value("chartHeight", 250)
        .build())
```

Avoid custom CSS for normal dashboard work. Prefer component types, `width(...)`, `twoColumn(...)`, `panel(...)`, and default layout values.

## Component Widths

Every component has `width(...)`:

| Width | Grid span |
| --- | --- |
| `full` | full row |
| `half` | 1/2 row |
| `third` | 1/3 row |
| `quarter` | 1/4 row |

Example:

```java
DashboardComponents.panel("queue", "Delivery Queue")
        .width("half")
        .child(/* child */)
        .build();
```

For exactly two widgets on one row, prefer `DashboardComponents.twoColumn(...)`:

```java
DashboardComponents.twoColumn("market-row")
        .child(DashboardComponents.chartPanel("price-chart", "Prices", DashboardComponents.LINE_CHART, "priceRows").build())
        .child(DashboardComponents.table("top-sellers", "Top Sellers", "sellerRows", columns).build())
        .build();
```

## Top KPI Row

Use `option("placement", "top")` for KPI groups that should render above the page header/tool row:

```java
DashboardComponents.kpiGroup("overview-kpis", "metrics", cards)
        .option("placement", "top")
        .build();
```

Use `option("compact", true)` when the group should use the compact four-card layout:

```java
DashboardComponents.kpiGroup("bank-kpis", "bank", cards)
        .option("compact", true)
        .build();
```

## Component Catalogue

Use constants from `DashboardComponents`.

| Type | Builder/helper | Data expected | Notes |
| --- | --- | --- | --- |
| `stack` | `DashboardComponents.stack(id)` | children | Vertical component group. |
| `panel` | `DashboardComponents.panel(id, title)` | children | Standard white UBS panel with title/subtitle. |
| `two-column` | `DashboardComponents.twoColumn(id)` | two or more children | Two-column row that stacks responsively. |
| `kpi-group` | `DashboardComponents.kpiGroup(id, dataPath, cards)` | object | Renders KPI cards from configured card paths. |
| `kpi-card` | direct type only | value/object | Reserved for single-card use; prefer `kpi-group`. |
| `alert-list` | direct type | array | Rows with `text`/`message` and `tone`. |
| `table` | `DashboardComponents.table(id, title, dataPath, columns)` | array | Data table with inferred or declared columns. |
| `key-value` | direct type | object or array | Summary fields. |
| `line-chart` | `DashboardComponents.chartPanel(...)` | array | Trend chart. |
| `bar-chart` | `DashboardComponents.chartPanel(...)` | array | Bar chart. |
| `status-chart` | `DashboardComponents.chartPanel(...)` | array | Built-in status distribution chart. |
| `shop-type-chart` | `DashboardComponents.chartPanel(...)` | array | Built-in shop type distribution chart. |
| `health-meters` | direct type | page performance object | UBS server health meter set. |
| `item-card-list` | direct type | array | Shop item cards; supports `detailRoute`. |
| `card-carousel` | direct type | array | Credit-card style carousel. |
| `roadmap` | direct type | array/object | Shop progression roadmap cards. |
| `action-form` | direct type | sections option | Named forms; preferred for real admin workflows. |
| `action-buttons` | direct type with actions | actions | Generic buttons with up to four freeform args; use only for quick/internal tools. |
| `command-runner` | direct type | none | Server command input and quick command buttons. |
| `output` | direct type | any | Preformatted output/log block. |
| `iframe` | direct type/widget `iframePath` | iframe URL/resource | Escape hatch for custom UI. |

Legacy `DashboardWidgetType` also defines `donut-chart`; modern component pages should use the chart types in `DashboardComponents` unless UBS adds a component donut renderer.

## KPI Groups

Cards are maps with:

| Key | Required | Meaning |
| --- | --- | --- |
| `label` | yes | Card label. |
| `path` | yes | Field inside the group data object. |
| `format` | no | Value formatter. |
| `hint` | no | Small explanatory text where supported. |

Example:

```java
DashboardComponents.kpiGroup("delivery-kpis", "metrics", List.of(
        Map.of("label", "Queued Orders", "path", "queuedOrders", "format", "number"),
        Map.of("label", "Revenue", "path", "revenue", "format", "money"),
        Map.of("label", "Failure Rate", "path", "failureRate", "format", "percent")
)).build();
```

Supported formats:

| Format | Use for |
| --- | --- |
| `money` | dollar values already in dollars |
| `cents-money` | integer cents |
| `number` | abbreviated numbers |
| `decimal2` | two decimal places |
| `percent` | numeric percent values |
| `percent-or-na` | percent that can be unavailable |
| `percent-string` | append `%` to string/number |
| `bytes` | byte counts |
| `ms` | milliseconds |
| `id` | shortened IDs |
| `boolean` | yes/no |
| `epoch-millis` | epoch timestamps |

If no format is supplied, UBS infers common formats from field names.

## Tables

Declared columns are maps with:

| Key | Required | Meaning |
| --- | --- | --- |
| `label` | yes | Column heading. |
| `path` | yes | Field path inside each row. |
| `format` | no | Formatter from the KPI format list. |

Example:

```java
List<Map<String, Object>> columns = List.of(
        Map.of("label", "Shop", "path", "shopName"),
        Map.of("label", "Queued", "path", "queued", "format", "number"),
        Map.of("label", "Revenue", "path", "revenue", "format", "money")
);

DashboardComponents.table("shops", "Delivery Shops", "shops", columns).build();
```

Useful table options:

```java
DashboardComponentDefinition.builder("shops-table", DashboardComponents.TABLE)
        .dataPath("shops")
        .option("columns", columns)
        .option("detailKey", "shopId")
        .option("detailRoute", "#/shops/{shopId}")
        .build();
```

`detailRoute` creates an `Open` column and replaces `{...}` with the row value.

## Key-Value Summaries

Use `key-value` for detail summaries:

```java
DashboardComponents.panel("order-summary", "Order Summary")
        .child(DashboardComponentDefinition.builder("order-kv", DashboardComponents.KEY_VALUE)
                .dataPath("order")
                .build())
        .build();
```

Input can be:

```json
{
  "orderId": "7d3...",
  "shopName": "Spawn Market",
  "status": "QUEUED",
  "total": 1250.0
}
```

or an array of `{label, value, format}` objects.

## Charts

Use `DashboardComponents.chartPanel(id, title, type, dataPath)`.

```java
DashboardComponents.chartPanel("revenue-trend", "Revenue Trend", DashboardComponents.LINE_CHART, "charts.revenue")
        .build();
```

General row shapes:

- line chart: rows with time/date labels and one or more numeric series
- bar chart: rows with a label/name field and numeric values
- status chart: rows with status labels and counts
- shop type chart: rows with shop type labels and counts

Keep chart rows small and already aggregated. Do not send full transaction histories when a 30-point trend is enough.

## Alerts

Use `alert-list` for status/warning rows:

```java
DashboardComponentDefinition.builder("delivery-alerts", DashboardComponents.ALERT_LIST)
        .dataPath("alerts")
        .build();
```

Rows:

```json
[
  {"tone": "ok", "text": "Courier queue healthy."},
  {"tone": "warn", "text": "2 orders failed delivery checks."}
]
```

Supported tones: `ok`, `success`, `warn`, `error`/other warning-like values.

## Action Forms

Use `action-form` for named form fields and admin workflows. This is the preferred replacement for loose `arg1`/`arg2` action buttons.

Example:

```java
DashboardComponentDefinition.builder("delivery-actions", DashboardComponents.ACTION_FORM)
        .title("Delivery Actions")
        .option("endpoint", "/api/webadmin/addons/youraddon/routes/overview/page-data/action")
        .option("sections", List.of(
                Map.of(
                        "title", "Retry Delivery",
                        "subtitle", "Retries one failed delivery order.",
                        "fields", List.of(
                                Map.of("id", "orderId", "label", "Order UUID", "type", "text", "placeholder", "Order UUID")
                        ),
                        "actions", List.of(
                                Map.of(
                                        "label", "Retry Order",
                                        "action", "RETRY_ORDER",
                                        "required", List.of("orderId"),
                                        "payload", Map.of("orderId", "$orderId")
                                )
                        )
                )
        ))
        .build();
```

`action-form` section schema:

| Key | Meaning |
| --- | --- |
| `title` | Section heading. |
| `subtitle` | Optional hint text. |
| `fields` | List of named fields. |
| `actions` | List of buttons using those fields. |

Field schema:

| Key | Meaning |
| --- | --- |
| `id` | Field ID used by payload references. |
| `label` | Visible label. |
| `type` | `text`, `number`, or `select`. |
| `placeholder` | Placeholder for inputs. |
| `value` | Initial value. |
| `options` | Select options as `{value,label}` objects. |

Action schema:

| Key | Meaning |
| --- | --- |
| `label` | Button text. |
| `action` | Sent as `action` in the POST body. |
| `payload` | POST body fields. `"$fieldId"` references form field values. |
| `required` | Field IDs that must be non-empty before posting. |
| `tone` | `danger`, `primary`, or omitted. |
| `confirm` | Optional confirmation text. |
| `refreshPage` | Defaults to true. |

Your endpoint should return:

```json
{
  "ok": true,
  "message": "Order retried."
}
```

## Action Buttons

`action-buttons` is a compact/generic action component. It renders buttons from `DashboardActionDefinition` and includes four optional freeform argument inputs. It is useful for internal debug tools, but not for polished addon UI.

Prefer `action-form` when players/admins need clear instructions.

Example:

```java
DashboardComponentDefinition.Builder actions =
        DashboardComponentDefinition.builder("maintenance-actions", DashboardComponents.ACTION_BUTTONS)
                .option("endpoint", "/api/webadmin/addons/youraddon/actions/maintenance/tools");

actions.action(DashboardActionDefinition.builder("rebuild-index", "Rebuild Index")
        .option("action", "REBUILD_INDEX")
        .confirm("Rebuild the addon index now?")
        .build());
```

## Command Runner

Use `command-runner` only for server-admin command tools:

```java
DashboardComponents.panel("server-tools", "Server Tools")
        .child(DashboardComponentDefinition.builder("commands", DashboardComponents.COMMAND_RUNNER)
                .option("endpoint", "/api/webadmin/command")
                .option("quickCommands", List.of("/ubs web status", "/ubs web on"))
                .build())
        .build();
```

## Output

Use `output` for logs or JSON-ish responses:

```java
DashboardComponentDefinition.builder("audit-output", DashboardComponents.OUTPUT)
        .dataPath("entries")
        .build();
```

Options:

- `placeholder`: text to show when data is empty

## Item Cards, Card Carousel, Roadmap, Health Meters

These are UBS-specific reusable components:

- `item-card-list`: item market/search cards; set `option("detailRoute", "#/shop-items/{itemId}")`
- `card-carousel`: credit-card style cards; data should include card-like rows
- `roadmap`: progression cards; useful for level/capacity unlocks
- `health-meters`: UBS health metrics; usually only useful for server health pages

Use them when your data matches the shape. Otherwise use `panel`, `kpi-group`, `table`, `key-value`, and charts.

## Iframes And Static Resources

Use iframes only when component pages cannot express the UI.

Register resources:

```java
DashboardDefinition.builder("youraddon", "Your Addon")
        .resourceRoot(YourAddon.class, "/assets/youraddon/webadmin")
        .panel(DashboardPanelDefinition.builder("advanced", "Advanced")
                .widget(DashboardWidgetDefinition.builder("custom-ui", DashboardWidgetType.IFRAME)
                        .iframePath("advanced.html")
                        .build())
                .build())
        .build();
```

UBS serves iframe resources at:

```text
/ubs-admin/addons/{modId}/{path}
```

Do not use iframes for normal tables, KPIs, charts, or forms. Component pages keep addon dashboards visually consistent and easier to maintain.

## Styling Rules For Addons

Do:

- use `DashboardLayoutDefaults.ubs()`
- use `panel` for framed sections
- use `two-column` for side-by-side widgets
- use `kpi-group` with `placement=top` for top dashboard KPI rows
- use `action-form` for named inputs and clear admin actions
- return pre-aggregated data for charts and tables
- keep data snapshots stable and small

Do not:

- write custom CSS for normal dashboard spacing
- use iframes for simple UI
- place card panels inside other visual cards unless the component requires it
- expose raw `arg1`/`arg2` controls to users
- send enormous raw histories to charts
- rely on uppercase IDs

## Detail Pages

A detail page is just another page with route placeholders:

```java
DashboardPageDefinition.builder("order", "Order Detail")
        .routePattern("#/d/youraddon/orders/{orderId}")
        .dataUrl("/api/webadmin/addons/youraddon/routes/orders/page-data/{orderId}")
        .component(DashboardComponents.kpiGroup("order-kpis", "metrics", cards).build())
        .component(DashboardComponents.panel("order-summary", "Order Summary")
                .child(DashboardComponentDefinition.builder("order-kv", DashboardComponents.KEY_VALUE)
                        .dataPath("order")
                        .build())
                .build())
        .build();
```

Route placeholders are expanded into `dataUrl`, action endpoints, and `detailRoute` values.

## Complete Panel Pattern For Another Agent

When asking another Codex chat to build an addon panel, give it this checklist:

```text
Build a UBS dashboard addon panel using docs/wiki/Dashboard-Addon-API.md.

Requirements:
- Register a DashboardDefinition for modId "<your mod id>".
- Add a DashboardPanelDefinition with id "<panel id>" so it appears in the left nav.
- Add a DashboardPageDefinition with the same id and routePattern "#/d/<mod id>/<panel id>".
- Use DashboardLayoutDefaults.ubs().
- Use component pages, not iframe/custom CSS.
- Put top KPIs in a kpi-group with option("placement", "top").
- Use panels and twoColumn for layout.
- Use tables/key-value/charts/action-form widgets as needed.
- Expose page data through a UBS addon route widget:
  /api/webadmin/addons/<mod id>/routes/<panel id>/page-data/snapshot
- Return one stable JSON snapshot and bind components with dataPath.
- Validate with the UBS web dashboard and compare against the built-in dashboard style.
```

## Testing Checklist

- Your mod depends on `ultimatebankingsystem`.
- `registerDashboard(...)` returns `success() == true`.
- Your dashboard appears in the dashboard switcher.
- Each left-nav item has both a `panel` and a matching `page`.
- Page `dataUrl` returns valid JSON.
- Component IDs are lowercase and unique.
- Top KPI groups render above the page header when intended.
- Two-column sections collapse correctly on narrow screens.
- Tables have declared columns for important rows.
- Actions return `{ok,message}`.
- No generic `arg1`/`arg2` fields are visible in polished UI.
- No custom CSS or iframe is used unless there is a documented reason.
