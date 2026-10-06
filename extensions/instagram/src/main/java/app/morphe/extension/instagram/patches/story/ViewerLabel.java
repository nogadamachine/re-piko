package app.morphe.extension.instagram.patches.story;

/** Formats public profile metadata already present in the story viewer response. */
public final class ViewerLabel {
    private ViewerLabel() { }

    public static String format(String username, String fullName) {
        if (username == null || username.trim().isEmpty()) return null;
        username = username.trim();
        String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.equals(username) || name.equals("@" + username)) {
            return "@" + username;
        }
        return "@" + username + "\n" + name;
    }
}
