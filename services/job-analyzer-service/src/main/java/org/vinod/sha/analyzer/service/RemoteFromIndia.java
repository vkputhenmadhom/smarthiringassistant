package org.vinod.sha.analyzer.service;

import java.util.regex.Pattern;

/**
 * Decides from a remote job's text whether someone in India can do it: the posting must say it is open
 * worldwide / anywhere, or name India, APAC or Asia, and must not limit it to the US (work authorisation,
 * citizenship, clearance, living there) or to another region.
 */
final class RemoteFromIndia {

    private static final String US = "(?-i:US|U\\.S\\.?|USA)|United States";

    private static final Pattern OPEN = Pattern.compile(
            "(?i)\\b(worldwide|world-wide|anywhere in the world|work from anywhere|from anywhere|globally"
                    + "|global(ly)? remote|remote[- ]global|fully distributed|location[- ]independent"
                    + "|any (country|location|time ?zone)|india|indian|apac|asia[- ]pacific|asia)\\b");

    private static final Pattern US_OR_OTHER_REGION_ONLY = Pattern.compile(
            "(?i)(authori[sz]ed to work in (the )?(" + US + ")"
                    + "|work authori[sz]ation (in|for) (the )?(" + US + ")"
                    + "|(" + US + ")[- ]?(citizens?|citizenship|persons?|residents?|based|only)\\b"
                    + "|green card|security clearance|\\bW-?2\\b|\\bITAR\\b"
                    + "|(reside|located|based|live|living|residing) (in|within) (the )?(" + US + "|Canada|Europe|the EU|the UK|LATAM|Latin America)\\b"
                    + "|anywhere in (the )?(" + US + "|North America|Canada|Europe|the EU|EU|UK|LATAM|Latin America)\\b"
                    + "|remote[ ,(/-]*(in (the )?)?(" + US + ")\\b"
                    + "|\\((" + US + ")\\)"
                    + "|(" + US + ") remote\\b)");

    private RemoteFromIndia() {
    }

    static boolean isOpenToIndia(String title, String description) {
        String text = (title == null ? "" : title) + "\n" + (description == null ? "" : description);
        return OPEN.matcher(text).find() && !US_OR_OTHER_REGION_ONLY.matcher(text).find();
    }
}
