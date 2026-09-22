package org.prebid.mobile.daro;

import android.text.TextUtils;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.PlacementType;
import org.prebid.mobile.rendering.models.TrackingEvent;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.video.vast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Internal optional end card. It is not a second transaction creative or ad impression. */
public final class DaroBannerCompanionModel extends CreativeModel {
    private final ArrayList<String> errorUrls;
    private final boolean required;
    private boolean errorSent;

    private DaroBannerCompanionModel(AdUnitConfiguration config, ArrayList<String> errors, boolean required) {
        super(TrackingManager.getInstance(), new OmEventTracker(), config);
        this.errorUrls = errors;
        this.required = required;
        setName("HTML");
        setHasEndCard(true);
        setRequireImpressionUrl(false);
    }

    public static boolean isInBanner(AdUnitConfiguration config) {
        return config.isBuiltInVideo() && config.getPlacementTypeValue() == PlacementType.IN_BANNER.getValue();
    }

    public boolean isRequired() { return required; }

    public void trackLoadFailure() {
        if (errorSent) return;
        errorSent = true;
        VastErrorTracker.fire(errorUrls, required ? 602 : 603);
    }

    public static DaroBannerCompanionModel fromVast(AdResponseParserVast root, AdUnitConfiguration config) {
        List<List<Creative>> chain = new ArrayList<>();
        for (AdResponseParserVast parser = root; parser != null; parser = parser.getWrappedVASTXml()) {
            Ad ad = parser.getVast().getAds().get(0);
            List<Creative> creatives = ad.getInline() != null ? ad.getInline().getCreatives()
                    : ad.getWrapper() != null ? ad.getWrapper().getCreatives() : null;
            if (creatives != null) chain.add(creatives);
        }
        int width = 0, height = 0;
        for (AdSize size : config.getSizes()) { width = size.getWidth(); height = size.getHeight(); break; }
        Companion selected = null;
        int selectedDepth = -1;
        for (int depth = chain.size() - 1; depth >= 0 && selected == null; depth--) {
            for (Creative creative : chain.get(depth)) {
                if (creative.getCompanionAds() == null) continue;
                for (Companion candidate : creative.getCompanionAds()) {
                    if (supported(candidate) && satisfiesRequired(chain, candidate)
                            && better(candidate, selected, width, height)) selected = candidate;
                }
            }
            if (selected != null) selectedDepth = depth;
        }
        boolean required = !satisfiesRequired(chain, null);
        if (required && selected == null) {
            VastErrorTracker.fire(root.getErrorUrls(), 602);
            throw new IllegalArgumentException("Required banner companions cannot fit the single end-card slot");
        }
        if (selected == null) return null;
        AdUnitConfiguration endConfig = new AdUnitConfiguration();
        endConfig.setAdFormat(AdFormat.BANNER);
        endConfig.setPlacementType(PlacementType.IN_BANNER);
        endConfig.addSize(new AdSize(width, height));
        endConfig.setHasEndCard(true);
        DaroBannerCompanionModel model = new DaroBannerCompanionModel(endConfig, root.getErrorUrls(), required);
        model.setWidth(width);
        model.setHeight(height);
        String click = safeClick(selected.getCompanionClickThrough() == null ? null : selected.getCompanionClickThrough().getValue());
        model.setClickUrl(click);
        model.setHtml(html(selected.getStaticResource().getValue(), click));
        ArrayList<String> views = new ArrayList<>();
        ArrayList<String> clicks = new ArrayList<>();
        collect(selected, views, clicks);
        // Tracking-only Wrapper companions apply to the matching Inline resource (VAST 3 §2.4.1.6).
        for (int depth = 0; depth < selectedDepth; depth++) {
            for (Creative creative : chain.get(depth)) {
                if (creative.getCompanionAds() == null) continue;
                for (Companion companion : creative.getCompanionAds()) {
                    if (companion.getStaticResource() == null && companion.getHtmlResource() == null
                            && companion.getIFrameResource() == null && matches(companion, selected)) {
                        collect(companion, views, clicks);
                    }
                }
            }
        }
        model.registerTrackingEvent(TrackingEvent.Events.IMPRESSION, views);
        model.registerTrackingEvent(TrackingEvent.Events.CLICK, clicks);
        return model;
    }

    private static boolean satisfiesRequired(List<List<Creative>> chain, Companion candidate) {
        for (List<Creative> creatives : chain) {
            for (Creative creative : creatives) {
                String requirement = creative.getCompanionsRequired();
                if (!"all".equals(requirement) && !"any".equals(requirement)) continue;
                if (candidate == null) return false;
                List<Companion> companions = creative.getCompanionAds();
                if (companions == null || companions.isEmpty()) return false;
                int matched = 0;
                for (Companion companion : companions) {
                    if (companion == candidate || (companion.getStaticResource() == null && companion.getHtmlResource() == null
                            && companion.getIFrameResource() == null && matches(companion, candidate))) matched++;
                }
                if (matched == 0 || ("all".equals(requirement) && matched != companions.size())) return false;
            }
        }
        return true;
    }

    private static void collect(Companion companion, ArrayList<String> views, ArrayList<String> clicks) {
        if (companion.getTrackingEvents() != null) {
            for (Tracking tracking : companion.getTrackingEvents()) {
                if ("creativeView".equals(tracking.getEvent())) add(views, tracking.getValue());
            }
        }
        for (CompanionClickTracking tracking : companion.getCompanionClickTrackings()) add(clicks, tracking.getValue());
    }

    private static void add(ArrayList<String> urls, String url) {
        if (url != null && !url.trim().isEmpty() && !urls.contains(url)) urls.add(url);
    }

    private static boolean matches(Companion a, Companion b) {
        if (!TextUtils.isEmpty(a.getId()) && !TextUtils.isEmpty(b.getId())) return a.getId().equals(b.getId());
        return positive(a.getWidth()) > 0 && positive(a.getHeight()) > 0
                && positive(a.getWidth()) == positive(b.getWidth()) && positive(a.getHeight()) == positive(b.getHeight());
    }

    static boolean supported(Companion companion) {
        String mode = companion.getRenderingMode();
        if (mode != null && !mode.isEmpty() && !"default".equals(mode) && !"end-card".equals(mode)) return false;
        StaticResource resource = companion.getStaticResource();
        if (resource == null || TextUtils.isEmpty(resource.getValue())) return false;
        String url = resource.getValue().toLowerCase(Locale.ROOT);
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false;
        String type = resource.getCreativeType();
        return "image/jpeg".equalsIgnoreCase(type) || "image/png".equalsIgnoreCase(type) || "image/gif".equalsIgnoreCase(type);
    }

    private static boolean better(Companion candidate, Companion previous, int width, int height) {
        if (previous == null) return true;
        boolean explicit = "end-card".equals(candidate.getRenderingMode());
        boolean oldExplicit = "end-card".equals(previous.getRenderingMode());
        if (explicit != oldExplicit) return explicit;
        return distance(candidate, width, height) < distance(previous, width, height);
    }

    private static double distance(Companion companion, int width, int height) {
        int w = positive(companion.getWidth()), h = positive(companion.getHeight());
        if (w == 0 || h == 0 || width <= 0 || height <= 0) return Double.POSITIVE_INFINITY;
        return Math.abs(Math.log((double) w * height / ((double) h * width)));
    }

    private static int positive(String value) {
        try { return Math.max(0, Integer.parseInt(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    static String safeClick(String click) {
        if (TextUtils.isEmpty(click)) return null;
        // Browser URL parsing removes ASCII tabs/newlines; reject them before scheme validation.
        for (int i = 0; i < click.length(); i++) {
            char c = click.charAt(i);
            if (c < 0x20 || c == 0x7f) return null;
        }
        String url = click.trim();
        int colon = url.indexOf(':');
        if (colon <= 0) return null;
        String scheme = url.substring(0, colon);
        if (!scheme.matches("[A-Za-z][A-Za-z0-9+.-]*")) return null;
        switch (scheme.toLowerCase(Locale.ROOT)) {
            case "javascript": case "data": case "file": case "content": case "blob": case "about":
                return null;
            default: return url; // HTTP(S), intent and app deep links go through the existing click handler.
        }
    }

    static String html(String image, String click) {
        String img = "<img id=\"companion\" src=\"" + TextUtils.htmlEncode(image)
                + "\" style=\"width:100%;height:100%;object-fit:contain;display:block\">";
        if (!TextUtils.isEmpty(click)) img = "<a style=\"display:block;width:100%;height:100%\" href=\""
                + TextUtils.htmlEncode(click) + "\">" + img + "</a>";
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}</style>"
                + "</head><body>" + img + "</body></html>";
    }
}
