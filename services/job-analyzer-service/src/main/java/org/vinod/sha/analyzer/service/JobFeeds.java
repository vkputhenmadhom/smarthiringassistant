package org.vinod.sha.analyzer.service;

import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.config.JobSyncProperties;
import org.vinod.sha.analyzer.config.JobSyncProperties.Feed;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The job feeds from job-feeds.yml: what each one imports, which jobs belong to it, and its portal tab.
 * An imported job is tagged PROVIDER_KEY_MARKET (e.g. ADZUNA_TECH_IN), so it is in its feed's job market
 * (see {@link JobMarket}) and in exactly one feed.
 */
@Component
public class JobFeeds {

    private final List<Feed> feeds;
    private final Map<String, Rules> rules;

    public JobFeeds(JobSyncProperties props) {
        this.feeds = props.getFeeds() == null ? List.of() : List.copyOf(props.getFeeds());
        this.rules = feeds.stream().collect(Collectors.toMap(Feed::getKey, Rules::of));
    }

    public List<Feed> all() {
        return feeds;
    }

    public Optional<Feed> find(String key) {
        return key == null ? Optional.empty() : feeds.stream().filter(f -> f.getKey().equalsIgnoreCase(key.trim())).findFirst();
    }

    /** The source tag for a job a feed imports from a provider, e.g. ADZUNA_TECH_IN */
    public static String sourceTag(String provider, Feed feed) {
        return (provider + "_" + feed.getKey() + "_" + feed.getMarket()).toUpperCase(Locale.ROOT);
    }

    /**
     * Prefix of an imported job's external id. Feeds of one market share it per provider, so a posting
     * two feeds find is imported once, by the first.
     */
    public static String externalIdPrefix(String provider, Feed feed) {
        return (provider + "_" + feed.getMarket()).toUpperCase(Locale.ROOT);
    }

    /** True if the job is listed under the feed: an import tagged for it, or, for a posted-in-app feed, a job posted here. */
    public static boolean includes(Feed feed, JobAnalysis job) {
        String source = job.getSource() == null ? "" : job.getSource().trim().toUpperCase(Locale.ROOT);
        if (feed.isPostedInApp()) {
            return source.isEmpty() || source.equals("INTERNAL");
        }
        return source.endsWith(("_" + feed.getKey() + "_" + feed.getMarket()).toUpperCase(Locale.ROOT));
    }

    /** Applies the feed's require-any / reject-any rules to a job's title and description. */
    public boolean accepts(Feed feed, String title, String description) {
        return rules.get(feed.getKey()).accepts((title == null ? "" : title) + "\n" + (description == null ? "" : description));
    }

    private record Rules(Pattern requireAny, Pattern rejectAny) {
        static Rules of(Feed feed) {
            return new Rules(anyOf(feed.getRequireAny()), anyOf(feed.getRejectAny()));
        }

        /** One case-insensitive pattern matching any of the given ones; null when there are none. */
        private static Pattern anyOf(List<String> patterns) {
            if (patterns == null || patterns.isEmpty()) {
                return null;
            }
            return Pattern.compile(patterns.stream().map(p -> "(?:" + p + ")").collect(Collectors.joining("|")),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        boolean accepts(String text) {
            return (requireAny == null || requireAny.matcher(text).find())
                    && (rejectAny == null || !rejectAny.matcher(text).find());
        }
    }
}
