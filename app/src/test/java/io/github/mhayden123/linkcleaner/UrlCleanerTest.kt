package io.github.mhayden123.linkcleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlCleanerTest {

    private fun clean(input: String) = UrlCleaner.clean(input)!!.cleaned

    @Test fun stripsUtmAndFbclid() = assertEquals(
        "https://example.com/article?id=42",
        clean("https://example.com/article?utm_source=fb&id=42&utm_medium=social&fbclid=IwAR123"),
    )

    @Test fun keepsFragmentAndNonTrackingParams() = assertEquals(
        "https://example.com/page?q=hello+world#section-2",
        clean("https://example.com/page?q=hello+world&gclid=abc#section-2"),
    )

    @Test fun removesQuestionMarkWhenAllParamsAreTracking() = assertEquals(
        "https://example.com/a",
        clean("https://example.com/a?utm_campaign=x"),
    )

    @Test fun extractsUrlFromSurroundingText() = assertEquals(
        "https://news.example.org/story",
        clean("Check this out! https://news.example.org/story?utm_source=share."),
    )

    @Test fun youtubeShareLink() = assertEquals(
        "https://youtu.be/dQw4w9WgXcQ?t=42",
        clean("https://youtu.be/dQw4w9WgXcQ?si=AbCdEfGh123&t=42"),
    )

    @Test fun youtubeWatchKeepsVideoId() = assertEquals(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared&si=xyz"),
    )

    @Test fun spotify() = assertEquals(
        "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
        clean("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=a1b2c3d4"),
    )

    @Test fun siIsNotStrippedOnOtherSites() = assertEquals(
        "https://example.com/?si=1",
        clean("https://example.com/?si=1"),
    )

    @Test fun twitterStatus() = assertEquals(
        "https://x.com/user/status/1234567890",
        clean("https://x.com/user/status/1234567890?s=46&t=AbCdEf"),
    )

    @Test fun instagramKeepsCarouselIndex() = assertEquals(
        "https://www.instagram.com/p/Cxyz123/?img_index=2",
        clean("https://www.instagram.com/p/Cxyz123/?igsh=MWQ1ZGUxMzBkMA==&img_index=2"),
    )

    @Test fun githubRefIsKept() = assertEquals(
        "https://github.com/owner/repo/blob/main/README.md?ref=dev",
        clean("https://github.com/owner/repo/blob/main/README.md?ref=dev"),
    )

    @Test fun googleRedirect() = assertEquals(
        "https://example.com/page?a=1",
        clean("https://www.google.com/url?sa=t&rct=j&q=&url=https%3A%2F%2Fexample.com%2Fpage%3Fa%3D1%26utm_source%3Dgoogle&ved=abc"),
    )

    @Test fun facebookRedirect() = assertEquals(
        "https://example.com/x",
        clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fx%3Ffbclid%3DIwAR0&h=AT0abc"),
    )

    @Test fun nestedRedirects() = assertEquals(
        "https://example.com/deep",
        clean(
            "https://www.google.com/url?q=" +
                java.net.URLEncoder.encode(
                    "https://l.facebook.com/l.php?u=" +
                        java.net.URLEncoder.encode("https://example.com/deep?utm_source=x", "UTF-8"),
                    "UTF-8",
                ),
        ),
    )

    @Test fun amazonProductTrimmed() = assertEquals(
        "https://www.amazon.com/dp/B08N5WRWNW",
        clean("https://www.amazon.com/Echo-Dot-4th-Gen/dp/B08N5WRWNW/ref=sr_1_1?crid=2ABC&keywords=echo+dot&qid=1700000000&sr=8-1&tag=someaffil-20"),
    )

    @Test fun amazonGpProductAndOtherCountry() = assertEquals(
        "https://www.amazon.co.uk/dp/B0ABCDEF12",
        clean("https://www.amazon.co.uk/gp/product/B0ABCDEF12?pf_rd_r=XYZ&pd_rd_w=abc"),
    )

    @Test fun amazonSearchKeepsKeywords() = assertEquals(
        "https://www.amazon.com/s?k=usb+cable",
        clean("https://www.amazon.com/s?k=usb+cable&crid=1X&sprefix=usb%2Caps%2C150&ref=nb_sb_noss"),
    )

    @Test fun ebayItemTrimmed() = assertEquals(
        "https://www.ebay.com/itm/123456789012",
        clean("https://www.ebay.com/itm/Some-Product-Title/123456789012?hash=item1&_trkparms=abc&mkevt=1"),
    )

    @Test fun aliexpressItemTrimmed() = assertEquals(
        "https://www.aliexpress.com/item/1005001234567890.html",
        clean("https://www.aliexpress.com/item/1005001234567890.html?spm=a2g0o.home&pvid=abc&algo_pvid=xyz"),
    )

    @Test fun walmartItemTrimmed() = assertEquals(
        "https://www.walmart.com/ip/123456789",
        clean("https://www.walmart.com/ip/Some-Product-Name/123456789?athbdg=L1600&from=/search"),
    )

    // Real link shared from a World Market Facebook/Instagram ad.
    @Test fun worldMarketAdLink() = assertEquals(
        "https://www.worldmarket.com/p/monster-energy-m3-super-concentrate-656799.html",
        clean(
            "https://www.worldmarket.com/p/monster-energy-m3-super-concentrate-656799.html" +
                "?media_type=image&ppc=true&camp=sp:facebook_instagram:|OG_PDSOC_MULTI_META_SALE_ASC_LOW_PURCH_NTEST_VO" +
                "|OG_META_CRM_ActiveCustomers_NATL_ALL_18-65plus_NTEST_VO-Dynamic" +
                "|OG_META_DYN_STND_PROD_NA_BRAND_EVG_XCAT_RefreshV1_06082026-12312026|52560464665609",
        ),
    )

    @Test fun campIsNotStrippedOnOtherSites() = assertEquals(
        "https://example.com/summer?camp=7",
        clean("https://example.com/summer?camp=7"),
    )

    @Test fun alreadyCleanLinkIsUnchanged() {
        val r = UrlCleaner.clean("https://example.com/clean/path")!!
        assertEquals("https://example.com/clean/path", r.cleaned)
        assertEquals(emptyList<String>(), r.changes)
    }

    @Test fun reportsRemovedParams() {
        val r = UrlCleaner.clean("https://example.com/?utm_source=a&fbclid=b")!!
        assertEquals(listOf("Removed: utm_source, fbclid"), r.changes)
    }

    @Test fun stripAllRemovesQueryAndFragment() = assertEquals(
        "https://shop.example.com/p/item-123.html",
        UrlCleaner.stripAll("https://shop.example.com/p/item-123.html?color=red&src=ad#reviews"),
    )

    @Test fun stripAllReturnsNullWhenNothingToRemove() =
        assertNull(UrlCleaner.stripAll("https://shop.example.com/p/item-123.html"))

    @Test fun noUrlReturnsNull() = assertNull(UrlCleaner.clean("just some text, no link here"))
}
