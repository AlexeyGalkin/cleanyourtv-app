package tv.cleanyour.ru.widgets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The F1 season from Jolpica-F1 (Ergast-compatible JSON): the calendar, both championships and the last race. Pure
 * Java on org.json, so the JVM tests read the same fixtures the TV reads. Numbers arrive as strings.
 */
final class F1 {
    private F1() {
    }

    enum Kind {
        FP1("FirstPractice", 60), FP2("SecondPractice", 60), FP3("ThirdPractice", 60),
        SPRINT_QUALI("SprintQualifying", 45), SPRINT("Sprint", 45), QUALI("Qualifying", 60), RACE(null, 120);

        final String key;
        final int minutes;

        Kind(String key, int minutes) {
            this.key = key;
            this.minutes = minutes;
        }
    }

    static final class Session {
        final Kind kind;
        final Instant start;

        Session(Kind kind, Instant start) {
            this.kind = kind;
            this.start = start;
        }

        Instant end() {
            return start.plusSeconds(kind.minutes * 60L);
        }
    }

    static final class Race {
        final int round;
        final String name;          // Jolpica's English name, "São Paulo Grand Prix"
        final String circuitId;
        final String circuitName;
        final String locality;
        final String country;
        final double lat;
        final double lon;
        final List<Session> sessions; // by start

        Race(int round, String name, String circuitId, String circuitName, String locality, String country,
             double lat, double lon, List<Session> sessions) {
            this.round = round;
            this.name = name;
            this.circuitId = circuitId;
            this.circuitName = circuitName;
            this.locality = locality;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
            this.sessions = sessions;
        }

        Session race() {
            return sessions.get(sessions.size() - 1);
        }

        boolean sprint() {
            for (Session s : sessions) if (s.kind == Kind.SPRINT) return true;
            return false;
        }
    }

    static final class Season {
        final String year;
        final List<Race> races;

        Season(String year, List<Race> races) {
            this.year = year;
            this.races = races;
        }

        /** The race weekend to show: the one under way, else the next one; null after the last race. */
        Race current(Instant now) {
            for (Race r : races) if (now.isBefore(r.race().end().plusSeconds(4 * 3600))) return r;
            return null;
        }

        /** Grands Prix and sprints still to run after {@code now} (for the title maths). */
        int[] remaining(Instant now) {
            int gps = 0, sprints = 0;
            for (Race r : races) {
                for (Session s : r.sessions) {
                    if (!s.start.isAfter(now)) continue;
                    if (s.kind == Kind.RACE) gps++;
                    if (s.kind == Kind.SPRINT) sprints++;
                }
            }
            return new int[] {gps, sprints};
        }
    }

    static final class Standing {
        final int position;
        final String id;        // driverId or constructorId
        final String given;     // "Max" (drivers only)
        final String family;    // "Verstappen", or the team name
        final String code;      // "VER"
        final String number;    // "3"
        final String teamId;    // the driver's (last) team
        final String teamName;
        final double points;
        final int wins;

        Standing(int position, String id, String given, String family, String code, String number, String teamId,
                 String teamName, double points, int wins) {
            this.position = position;
            this.id = id;
            this.given = given;
            this.family = family;
            this.code = code;
            this.number = number;
            this.teamId = teamId;
            this.teamName = teamName;
            this.points = points;
            this.wins = wins;
        }
    }

    static final class Table {
        final String season;
        final int round;
        final List<Standing> rows;

        Table(String season, int round, List<Standing> rows) {
            this.season = season;
            this.round = round;
            this.rows = rows;
        }
    }

    static final class Result {
        final String position;  // "1", or "R" retired
        final String driverId;
        final String given;
        final String family;
        final String teamId;
        final String teamName;
        final String time;      // "2:20:28.772", "+1.234", or the status
        final double points;
        final boolean fastest;

        Result(String position, String driverId, String given, String family, String teamId, String teamName,
               String time, double points, boolean fastest) {
            this.position = position;
            this.driverId = driverId;
            this.given = given;
            this.family = family;
            this.teamId = teamId;
            this.teamName = teamName;
            this.time = time;
            this.points = points;
            this.fastest = fastest;
        }
    }

    static final class LastRace {
        final int round;
        final String name;
        final String circuitId;
        final List<Result> results;
        final String fastestLap; // "1:19.328" or ""

        LastRace(int round, String name, String circuitId, List<Result> results, String fastestLap) {
            this.round = round;
            this.name = name;
            this.circuitId = circuitId;
            this.results = results;
            this.fastestLap = fastestLap;
        }
    }

    private static JSONObject mr(String json) throws JSONException {
        return new JSONObject(json).getJSONObject("MRData");
    }

    private static Instant at(JSONObject o) {
        String date = o.optString("date", "");
        String time = o.optString("time", "");
        if (date.isEmpty()) return null;
        if (time.isEmpty()) time = "12:00:00Z"; // a time not yet published: midday UTC, never shown as exact
        if (!time.endsWith("Z")) time = time + "Z";
        try {
            return Instant.parse(date + "T" + time);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Season season(String json) throws JSONException {
        JSONObject table = mr(json).getJSONObject("RaceTable");
        JSONArray list = table.getJSONArray("Races");
        List<Race> races = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.getJSONObject(i);
            List<Session> sessions = new ArrayList<>();
            for (Kind k : Kind.values()) {
                Instant start = k == Kind.RACE ? at(r) : (r.has(k.key) ? at(r.getJSONObject(k.key)) : null);
                if (start != null) sessions.add(new Session(k, start));
            }
            if (sessions.isEmpty() || sessions.get(sessions.size() - 1).kind != Kind.RACE) continue;
            Collections.sort(sessions, (a, b) -> a.start.compareTo(b.start));
            // A race must be the last session even if a feed lists it oddly.
            Session race = null;
            for (Session s : sessions) if (s.kind == Kind.RACE) race = s;
            sessions.remove(race);
            sessions.add(race);
            JSONObject c = r.getJSONObject("Circuit");
            JSONObject loc = c.optJSONObject("Location");
            races.add(new Race(r.optInt("round", 0), r.optString("raceName", "Grand Prix"), c.optString("circuitId", ""),
                    c.optString("circuitName", ""), loc == null ? "" : loc.optString("locality", ""),
                    loc == null ? "" : loc.optString("country", ""),
                    loc == null ? Double.NaN : loc.optDouble("lat", Double.NaN),
                    loc == null ? Double.NaN : loc.optDouble("long", Double.NaN), sessions));
        }
        Collections.sort(races, (a, b) -> a.race().start.compareTo(b.race().start));
        return new Season(table.optString("season", ""), races);
    }

    static Table drivers(String json) throws JSONException {
        JSONObject list = standingsList(json);
        List<Standing> rows = new ArrayList<>();
        if (list != null) {
            JSONArray a = list.getJSONArray("DriverStandings");
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                JSONObject d = s.getJSONObject("Driver");
                JSONArray teams = s.optJSONArray("Constructors");
                JSONObject team = teams == null || teams.length() == 0 ? null : teams.getJSONObject(teams.length() - 1);
                rows.add(new Standing(s.optInt("position", i + 1), d.optString("driverId", ""), d.optString("givenName", ""),
                        d.optString("familyName", ""), d.optString("code", ""), d.optString("permanentNumber", ""),
                        team == null ? "" : team.optString("constructorId", ""), team == null ? "" : team.optString("name", ""),
                        s.optDouble("points", 0), s.optInt("wins", 0)));
            }
        }
        return new Table(list == null ? "" : list.optString("season", ""), list == null ? 0 : list.optInt("round", 0), rows);
    }

    static Table constructors(String json) throws JSONException {
        JSONObject list = standingsList(json);
        List<Standing> rows = new ArrayList<>();
        if (list != null) {
            JSONArray a = list.getJSONArray("ConstructorStandings");
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                JSONObject c = s.getJSONObject("Constructor");
                String id = c.optString("constructorId", "");
                rows.add(new Standing(s.optInt("position", i + 1), id, "", c.optString("name", ""), "", "", id,
                        c.optString("name", ""), s.optDouble("points", 0), s.optInt("wins", 0)));
            }
        }
        return new Table(list == null ? "" : list.optString("season", ""), list == null ? 0 : list.optInt("round", 0), rows);
    }

    private static JSONObject standingsList(String json) throws JSONException {
        JSONArray lists = mr(json).getJSONObject("StandingsTable").optJSONArray("StandingsLists");
        return lists == null || lists.length() == 0 ? null : lists.getJSONObject(0);
    }

    static LastRace lastRace(String json) throws JSONException {
        JSONArray races = mr(json).getJSONObject("RaceTable").optJSONArray("Races");
        if (races == null || races.length() == 0) return null;
        JSONObject r = races.getJSONObject(0);
        JSONArray a = r.optJSONArray("Results");
        List<Result> results = new ArrayList<>();
        String fastest = "";
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                JSONObject d = s.getJSONObject("Driver");
                JSONObject c = s.optJSONObject("Constructor");
                JSONObject t = s.optJSONObject("Time");
                JSONObject fl = s.optJSONObject("FastestLap");
                boolean isFastest = fl != null && "1".equals(fl.optString("rank"));
                if (isFastest) {
                    JSONObject ft = fl.optJSONObject("Time");
                    fastest = ft == null ? "" : ft.optString("time", "");
                }
                String pos = s.optString("positionText", s.optString("position", ""));
                results.add(new Result(pos, d.optString("driverId", ""), d.optString("givenName", ""),
                        d.optString("familyName", ""), c == null ? "" : c.optString("constructorId", ""),
                        c == null ? "" : c.optString("name", ""),
                        t != null ? t.optString("time", "") : s.optString("status", ""), s.optDouble("points", 0), isFastest));
            }
        }
        return new LastRace(r.optInt("round", 0), r.optString("raceName", ""),
                r.optJSONObject("Circuit") == null ? "" : r.getJSONObject("Circuit").optString("circuitId", ""), results, fastest);
    }

    /**
     * Whether the leader can no longer be caught: lead &gt; what the second can still score (25 per Grand Prix, 8 per
     * sprint; no fastest-lap point since 2025).
     */
    static boolean clinched(Table drivers, int[] remaining) {
        if (drivers.rows.size() < 2) return false;
        double lead = drivers.rows.get(0).points - drivers.rows.get(1).points;
        return lead > remaining[0] * 25 + remaining[1] * 8;
    }

    /** The session to count down to, or the one live now; null when the weekend (and season) is over. */
    static Session next(Race race, Instant now) {
        if (race == null) return null;
        for (Session s : race.sessions) if (now.isBefore(s.end())) return s;
        return null;
    }
}
