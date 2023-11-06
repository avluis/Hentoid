package me.devsaki.hentoid.json.core;

import java.util.List;

import me.devsaki.hentoid.database.domains.Achievement;

public class JsonAchievements {

    public List<JsonAchievement> achievements;

    public static class JsonAchievement {
        public int id;
        public Achievement.Type type;
    }
}
