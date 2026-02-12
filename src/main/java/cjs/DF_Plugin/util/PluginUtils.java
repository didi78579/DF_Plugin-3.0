package cjs.DF_Plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.TimeUnit;

public final class PluginUtils {

    private PluginUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * '&' 문자를 사용한 색상 코드를 변환합니다.
     * @param message 색상을 적용할 문자열
     * @return 색상이 적용된 문자열
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Location 객체를 'world,x,y,z' 형태의 문자열로 변환합니다.
     * @param loc 변환할 Location
     * @return 직렬화된 문자열
     */
    public static String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * 'world,x,y,z' 형태의 문자열을 Location 객체로 변환합니다.
     * @param s 직렬화된 위치 문자열
     * @return 변환된 Location 객체, 실패 시 null
     */
    public static Location deserializeLocation(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length != 4) {
            return null;
        }
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(w, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 밀리초 단위의 시간을 "X시간 Y분" 또는 "Y분 Z초"와 같이 읽기 쉬운 형식으로 변환합니다.
     * @param millis 변환할 시간 (밀리초)
     * @return 형식화된 시간 문자열
     */
    public static String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format("%d시간 %d분", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds);
        } else {
            return String.format("%d초", seconds);
        }
    }
}