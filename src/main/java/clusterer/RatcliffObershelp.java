package clusterer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ratcliff/Obershelp pattern recognition (aka Gestalt Pattern Matching)
 * The similarity of two strings s1 and s2 is defined as the doubled number
 * of matching characters divided by the total number of characters in the
 * two strings. Matching characters are those characters found in the longest
 * common subsequence plus, recursively, the matching characters in the unmatched
 * region on either side of the longest common subsequence.
 */
public class RatcliffObershelp {

    /**
     * Compute the Ratcliff-Obershelp similarity between strings.
     *
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return The RatcliffObershelp similarity in the range [0, 1]
     * @throws NullPointerException if s1 or s2 is null.
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            throw new NullPointerException("one of the provided strings is null");
        }

        if (Objects.equals(s1, s2)) {
            return 1.0d;
        }

        final List<String> matches = matchingCharsList(s1, s2);
        int sumOfMatches = 0;

        for (String eachMatch : matches) {
            sumOfMatches += eachMatch.length();
        }

        return 2.0d * sumOfMatches / (s1.length() + s2.length());
    }

    /**
     * Return 1 - similarity.
     *
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return 1 - similarity
     * @throws NullPointerException if s1 or s2 is null.
     */
    public static double distance(final String s1, final String s2) {
        return 1.0d - similarity(s1, s2);
    }

    private static List<String> matchingCharsList(final String s1, final String s2) {
        List<String> list = new ArrayList<>();
        String match = frontMaxMatch(s1, s2);

        if (match.length() > 0) {
            String frontSource = s1.substring(0, s1.indexOf(match));
            String frontTarget = s2.substring(0, s2.indexOf(match));
            List<String> frontQueue = matchingCharsList(frontSource, frontTarget);

            String endSource = s1.substring(s1.indexOf(match) + match.length());
            String endTarget = s2.substring(s2.indexOf(match) + match.length());
            List<String> endQueue = matchingCharsList(endSource, endTarget);

            list.add(match);
            list.addAll(frontQueue);
            list.addAll(endQueue);
        }

        return list;
    }

    private static String frontMaxMatch(final String s1, final String s2) {
        int longest = 0;
        String longestSubstring = "";

        for (int i = 0; i < s1.length(); ++i) {
            for (int j = i + 1; j <= s1.length(); ++j) {
                String substring = s1.substring(i, j);
                if (s2.contains(substring) && substring.length() > longest) {
                    longest = substring.length();
                    longestSubstring = substring;
                }
            }
        }

        return longestSubstring;
    }
}