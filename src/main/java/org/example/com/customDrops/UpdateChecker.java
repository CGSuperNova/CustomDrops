package org.example.com.customDrops;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 更新检测工具类
 * 使用 GitHub Releases API 检查是否有新版本
 */
public class UpdateChecker {

    // 替换为你的 GitHub 用户名和仓库名
    private static final String GITHUB_API = "https://api.github.com/repos/你的用户名/CustomDrops/releases/latest";

    /**
     * 异步检查更新，如果有新版本则通知所有在线管理员
     */
    public static void checkUpdateAsync(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String latestVersion = getLatestVersion();
                    String currentVersion = plugin.getDescription().getVersion();
                    String releaseNotes = getReleaseNotes();

                    if (latestVersion == null) {
                        plugin.getLogger().warning("无法获取最新版本信息，请检查网络或 GitHub API 是否可用");
                        return;
                    }

                    // 版本比较（简单字符串比较，建议使用语义化版本比较库）
                    if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("  [CustomDrops] 发现新版本！");
                        plugin.getLogger().info("  当前版本: " + currentVersion);
                        plugin.getLogger().info("  最新版本: " + latestVersion);
                        if (releaseNotes != null && !releaseNotes.isEmpty()) {
                            plugin.getLogger().info("  更新内容: " + releaseNotes);
                        }
                        plugin.getLogger().info("  下载地址: https://github.com/你的用户名/CustomDrops/releases/latest");
                        plugin.getLogger().info("========================================");

                        // 通知所有在线管理员（有权限或 OP）
                        notifyOnlineAdmins(plugin, latestVersion, currentVersion);
                    } else {
                        plugin.getLogger().info("[CustomDrops] 当前已是最新版本 (" + currentVersion + ")");
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("[CustomDrops] 更新检测失败: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 获取最新版本号
     */
    private static String getLatestVersion() throws Exception {
        JsonObject json = fetchGitHubRelease();
        if (json == null) return null;
        return json.get("tag_name").getAsString();
    }

    /**
     * 获取更新内容（Release Body）
     */
    private static String getReleaseNotes() throws Exception {
        JsonObject json = fetchGitHubRelease();
        if (json == null) return null;
        if (json.has("body")) {
            return json.get("body").getAsString();
        }
        return null;
    }

    /**
     * 调用 GitHub API 获取最新 Release 信息
     */
    private static JsonObject fetchGitHubRelease() throws Exception {
        URL url = new URL(GITHUB_API);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            Gson gson = new Gson();
            return gson.fromJson(reader, JsonObject.class);
        }
    }

    /**
     * 通知所有在线管理员（OP 或拥有 customdrops.update 权限的玩家）
     */
    private static void notifyOnlineAdmins(JavaPlugin plugin, String latestVersion, String currentVersion) {
        String message = ChatColor.translateAlternateColorCodes('&',
                "&6[CustomDrops] &e发现新版本 &b" + latestVersion + " &e(当前: &7" + currentVersion + "&e) &a请前往 GitHub 更新！");
        String downloadMsg = ChatColor.translateAlternateColorCodes('&',
                "&7下载地址: &nhttps://github.com/你的用户名/CustomDrops/releases/latest");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("customdrops.update")) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "========== CustomDrops 更新提示 ==========");
                player.sendMessage(message);
                player.sendMessage(downloadMsg);
                player.sendMessage(ChatColor.GOLD + "=========================================");
                player.sendMessage("");
            }
        }
    }
}