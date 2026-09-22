package org.example.com.customDrops;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 更新检测工具类（1.1.0 重写）。
 * <p>
 * 相比 1.0.x 修复/改进了以下问题：
 * <ol>
 *   <li><b>仓库地址错误</b>：旧版硬编码了占位符 {@code 你的用户名}，导致请求 404，
 *       永远提示「无法获取最新版本信息」。现已改为真实仓库 {@value #DEFAULT_REPOSITORY}，
 *       并支持通过配置项 {@code update-check.repository} 覆盖。</li>
 *   <li><b>版本比较逻辑错误</b>：旧版用 {@code equalsIgnoreCase} 比较，
 *       GitHub 的 tag 是 {@code v1.0.4} 而插件版本是 {@code 1.0.4}，
 *       两者永不相等，导致「已是最新版本」时也会误报有新版本。
 *       现已改为语义化版本逐段比较（自动忽略 {@code v} 前缀与 {@code -SNAPSHOT} 等后缀）。</li>
 *   <li><b>重复网络请求</b>：旧版 {@code getLatestVersion()} 与 {@code getReleaseNotes()}
 *       各自请求一次 API，且没有关闭连接。现在只请求一次并复用结果。</li>
 *   <li><b>接口规范</b>：补充 {@code User-Agent}、{@code Accept}、超时与
 *       {@code disconnect()}，并对 403 限流（rate limit）单独给出提示。</li>
 *   <li><b>可观测性</b>：区分「网络不可达」「限流」「仓库不存在」「解析失败」，
 *       日志中给出具体原因，不再只输出一句「无法获取」。</li>
 *   <li><b>并发保护</b>：同一时间只允许一个检测任务在跑，避免 reload 连点导致请求风暴。</li>
 * </ol>
 */
@SuppressWarnings("deprecation") // ChatColor 仍是跨 1.17 ~ 最新版本最通用的文本着色方案
public class UpdateChecker {

    /** 真实的 GitHub 仓库（owner/repo） */
    public static final String DEFAULT_REPOSITORY = "CGSuperNova/CustomDrops";

    private static final Gson GSON = new Gson();

    /** 防止并发重复检测 */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /** 最近一次检测结果缓存 */
    private static volatile Result lastResult = null;

    /** 周期性检测任务 */
    private static BukkitTask periodicTask = null;

    /** 启动后延迟多久开始首次检测（tick，20 tick = 1 秒） */
    private static final long INITIAL_DELAY_TICKS = 40L;

    /** 周期检测间隔：6 小时 */
    private static final long PERIOD_TICKS = 20L * 60L * 60L * 6L;

    private UpdateChecker() {
    }

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 启动更新检测：延迟数秒后执行一次，随后每 6 小时执行一次。
     * 已有周期任务时会先取消旧任务，避免 {@code /customdrops reload} 造成任务堆积。
     *
     * @param plugin 插件实例
     */
    public static void start(CustomDropsPlugin plugin) {
        stop();
        periodicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> checkUpdateAsync(plugin),
                INITIAL_DELAY_TICKS,
                PERIOD_TICKS);
    }

    /** 停止周期检测任务 */
    public static void stop() {
        if (periodicTask != null) {
            try {
                periodicTask.cancel();
            } catch (IllegalStateException ignored) {
                // 插件正在禁用，任务已被取消
            }
            periodicTask = null;
        }
    }

    /**
     * 异步检查更新，发现新版本时通知在线管理员。
     *
     * @param plugin 插件实例
     */
    public static void checkUpdateAsync(CustomDropsPlugin plugin) {
        if (!RUNNING.compareAndSet(false, true)) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 已有一个更新检测任务在运行，跳过本次请求");
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Result result = fetch(plugin);
                lastResult = result;

                if (result.state != State.OK) {
                    plugin.getLogger().warning("[CustomDrops] 更新检测未完成: " + result.message);
                    return;
                }

                String current = plugin.getPluginVersion();
                if (result.isNewerThan(current)) {
                    logUpdate(plugin, result, current);
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOnlineAdmins(plugin, result, current));
                } else {
                    plugin.getLogger().info("[CustomDrops] 当前已是最新版本 (" + current + ")");
                }
            } finally {
                RUNNING.set(false);
            }
        });
    }

    /**
     * 同步获取一次检测结果（供命令使用，调用方需自行保证在异步线程执行）。
     */
    public static Result fetchNow(CustomDropsPlugin plugin) {
        Result result = fetch(plugin);
        lastResult = result;
        return result;
    }

    /** @return 最近一次检测结果，未检测过时返回 null */
    public static Result getLastResult() {
        return lastResult;
    }

    // ------------------------------------------------------------------
    // 核心请求逻辑
    // ------------------------------------------------------------------

    private static Result fetch(CustomDropsPlugin plugin) {
        String repository = plugin.getUpdateRepository();
        String apiUrl = "https://api.github.com/repos/" + repository + "/releases/latest";

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "CustomDrops-UpdateChecker/1.1.0");

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return Result.error(State.NOT_FOUND,
                        "仓库 " + repository + " 下没有已发布的 Release（HTTP 404）");
            }
            if (code == 403 || code == 429) {
                long reset = connection.getHeaderFieldLong("X-RateLimit-Reset", 0L);
                String hint = reset > 0
                        ? "，配额将于 " + new java.util.Date(reset * 1000L) + " 重置"
                        : "";
                return Result.error(State.RATE_LIMITED,
                        "GitHub API 访问受限或已达频率上限（HTTP " + code + "）" + hint);
            }
            if (code != HttpURLConnection.HTTP_OK) {
                return Result.error(State.HTTP_ERROR, "GitHub API 返回异常状态码 " + code);
            }

            JsonObject json;
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                json = GSON.fromJson(reader, JsonObject.class);
            }
            if (json == null || !json.has("tag_name") || json.get("tag_name").isJsonNull()) {
                return Result.error(State.PARSE_ERROR, "GitHub 返回内容中缺少 tag_name 字段");
            }

            String tag = json.get("tag_name").getAsString();
            String name = json.has("name") && !json.get("name").isJsonNull()
                    ? json.get("name").getAsString() : tag;
            String body = json.has("body") && !json.get("body").isJsonNull()
                    ? json.get("body").getAsString() : "";
            String htmlUrl = json.has("html_url") && !json.get("html_url").isJsonNull()
                    ? json.get("html_url").getAsString()
                    : "https://github.com/" + repository + "/releases/latest";
            boolean prerelease = json.has("prerelease") && json.get("prerelease").getAsBoolean();

            return Result.ok(tag, name, body, htmlUrl, prerelease);
        } catch (java.net.UnknownHostException | java.net.NoRouteToHostException e) {
            return Result.error(State.NETWORK_ERROR,
                    "无法连接 GitHub（请检查服务器网络与 DNS 是否可用）: " + e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            return Result.error(State.NETWORK_ERROR, "连接 GitHub 超时，请稍后重试");
        } catch (Exception e) {
            return Result.error(State.NETWORK_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void logUpdate(CustomDropsPlugin plugin, Result result, String current) {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  [CustomDrops] 发现新版本！");
        plugin.getLogger().info("  当前版本: " + current);
        plugin.getLogger().info("  最新版本: " + result.tagName + (result.prerelease ? " (预发布)" : ""));
        for (String line : firstLines(result.body, 8)) {
            plugin.getLogger().info("  " + line);
        }
        plugin.getLogger().info("  下载地址: " + result.htmlUrl);
        plugin.getLogger().info("========================================");
    }

    /**
     * 通知所有在线管理员（OP 或拥有 {@code customdrops.update} 权限的玩家）
     */
    private static void notifyOnlineAdmins(CustomDropsPlugin plugin, Result result, String current) {
        String message = color("&6[CustomDrops] &e发现新版本 &b" + result.tagName
                + " &e(当前: &7" + current + "&e) &a请前往 GitHub 更新！");
        String download = color("&7下载地址: &n" + result.htmlUrl);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("customdrops.update")) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "========== CustomDrops 更新提示 ==========");
                player.sendMessage(message);
                player.sendMessage(download);
                player.sendMessage(ChatColor.GOLD + "=========================================");
                player.sendMessage("");
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * 取文本的前若干行，用于把 Release 正文写进控制台时避免刷屏。
     */
    public static List<String> firstLines(String text, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            lines.add(line.trim());
            if (lines.size() >= maxLines) {
                break;
            }
        }
        return lines;
    }

    /**
     * 判断 {@code latest} 是否比 {@code current} 更新（语义化版本比较）。
     * <p>
     * 规则：忽略 {@code v}/{@code V} 前缀，按 {@code .} 与 {@code -} 切分成数字段逐位比较，
     * 数字段缺失视为 0；带预发布后缀（如 {@code -beta}）的版本视为低于同号正式版。
     *
     * @param latest  远端版本（如 {@code v1.1.0}）
     * @param current 本地版本（如 {@code 1.0.4}）
     * @return true 表示远端更新
     */
    public static boolean isNewer(String latest, String current) {
        if (latest == null || latest.isBlank()) {
            return false;
        }
        if (current == null || current.isBlank()) {
            return true;
        }
        int[] latestParts = parseVersion(latest);
        int[] currentParts = parseVersion(current);
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int left = i < latestParts.length ? latestParts[i] : 0;
            int right = i < currentParts.length ? currentParts[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        // 数字部分相同：正式版 > 预发布版
        boolean latestPre = isPrerelease(latest);
        boolean currentPre = isPrerelease(current);
        return currentPre && !latestPre;
    }

    private static int[] parseVersion(String version) {
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        // 去掉预发布/构建后缀（-SNAPSHOT、+build 等）
        int cut = indexOfAny(cleaned, '-', '+');
        if (cut >= 0) {
            cleaned = cleaned.substring(0, cut);
        }
        if (cleaned.isEmpty()) {
            return new int[0];
        }
        String[] tokens = cleaned.split("\\.");
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private static boolean isPrerelease(String version) {
        String cleaned = version.trim().toLowerCase(java.util.Locale.ROOT);
        return cleaned.contains("-snapshot") || cleaned.contains("-beta")
                || cleaned.contains("-alpha") || cleaned.contains("-rc")
                || cleaned.contains("-dev");
    }

    private static int indexOfAny(String text, char... chars) {
        int best = -1;
        for (char c : chars) {
            int index = text.indexOf(c);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    /** 检测结果状态 */
    public enum State {
        /** 成功取得 Release 信息 */
        OK,
        /** 仓库没有任何 Release */
        NOT_FOUND,
        /** 触发 GitHub API 限流 */
        RATE_LIMITED,
        /** HTTP 状态码异常 */
        HTTP_ERROR,
        /** 返回内容无法解析 */
        PARSE_ERROR,
        /** 网络不可达 / 超时 */
        NETWORK_ERROR
    }

    /** 一次更新检测的结果 */
    public static final class Result {
        private final State state;
        private final String message;
        private final String tagName;
        private final String releaseName;
        private final String body;
        private final String htmlUrl;
        private final boolean prerelease;
        private final long timestamp;

        private Result(State state, String message, String tagName, String releaseName,
                       String body, String htmlUrl, boolean prerelease) {
            this.state = state;
            this.message = message;
            this.tagName = tagName;
            this.releaseName = releaseName;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.prerelease = prerelease;
            this.timestamp = System.currentTimeMillis();
        }

        private static Result ok(String tagName, String releaseName, String body,
                                 String htmlUrl, boolean prerelease) {
            return new Result(State.OK, "ok", tagName, releaseName, body, htmlUrl, prerelease);
        }

        private static Result error(State state, String message) {
            return new Result(state, message, null, null, null, null, false);
        }

        public State getState() {
            return state;
        }

        public String getMessage() {
            return message;
        }

        public String getTagName() {
            return tagName;
        }

        public String getReleaseName() {
            return releaseName;
        }

        public String getBody() {
            return body == null ? "" : body;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public boolean isPrerelease() {
            return prerelease;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /** @return 远端版本是否比给定版本更新；检测失败时返回 false */
        public boolean isNewerThan(String currentVersion) {
            return state == State.OK && isNewer(tagName, currentVersion);
        }
    }
}
