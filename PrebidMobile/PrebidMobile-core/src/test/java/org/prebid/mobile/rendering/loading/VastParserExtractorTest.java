/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.loading;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.rendering.models.internal.VastExtractorResult;
import org.prebid.mobile.rendering.networking.modelcontrollers.AsyncVastLoader;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.test.utils.ResourceUtils;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.prebid.mobile.api.exceptions.AdException.INTERNAL_ERROR;
import static org.prebid.mobile.rendering.video.vast.VASTErrorCodes.WRAPPER_LIMIT_REACH_ERROR;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class VastParserExtractorTest {

    private VastParserExtractor vastParserExtractor;
    @Mock private VastParserExtractor.Listener mockListener;
    @Mock private AsyncVastLoader mockAsyncVastLoader;
    @Mock private AdResponseParserVast mockResponseParserVast;

    private String defaultResponseString;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockResponseParserVast.getVastUrl()).thenReturn("url");

        vastParserExtractor = new VastParserExtractor(mockListener);
        WhiteBox.field(VastParserExtractor.class, "asyncVastLoader").set(vastParserExtractor, mockAsyncVastLoader);
        defaultResponseString = ResourceUtils.convertResourceToString("vast.xml");
    }

    @Test
    public void emptyWrapperFiresResolvedErrorOnceAndIgnoresLateCallbacks() throws Exception {
        org.prebid.mobile.rendering.networking.tracking.TrackingManager tracker =
                mock(org.prebid.mobile.rendering.networking.tracking.TrackingManager.class);
        java.lang.reflect.Field singleton = org.prebid.mobile.rendering.networking.tracking.TrackingManager.class.getDeclaredField("sInstance");
        singleton.setAccessible(true);
        Object original = singleton.get(null);
        singleton.set(null, tracker);
        try {
            vastParserExtractor.extract(wrapper("", "first").replace("<Creatives/>",
                    "<Error>https://example.test/error?code=[ERRORCODE]</Error><Creatives/>"));
            vastParserExtractor.extract("<VAST version=\"3.0\"/>");
            vastParserExtractor.extract("<VAST version=\"3.0\"/>");
            verify(tracker).fireEventTrackingURL("https://example.test/error?code=303");
            verifyNoMoreInteractions(tracker);
            verify(mockListener).onResult(any());
        } finally {
            singleton.set(null, original);
        }
    }

    @Test
    public void nestedTimeoutReportsEveryWrapperWith301() throws Exception {
        org.prebid.mobile.rendering.networking.tracking.TrackingManager tracker =
                mock(org.prebid.mobile.rendering.networking.tracking.TrackingManager.class);
        java.lang.reflect.Field singleton = org.prebid.mobile.rendering.networking.tracking.TrackingManager.class.getDeclaredField("sInstance");
        singleton.setAccessible(true);
        Object original = singleton.get(null);
        singleton.set(null, tracker);
        try {
            for (String name : new String[]{"outer", "inner"}) {
                vastParserExtractor.extract(wrapper("", name).replace("<Creatives/>",
                        "<Error>https://example.test/" + name + "?code=[ERRORCODE]</Error><Creatives/>"));
            }
            ArgumentCaptor<org.prebid.mobile.rendering.networking.ResponseHandler> callback =
                    ArgumentCaptor.forClass(org.prebid.mobile.rendering.networking.ResponseHandler.class);
            verify(mockAsyncVastLoader, times(2)).loadVast(anyString(), callback.capture());
            callback.getValue().onError("timeout", 3000);
            callback.getValue().onError("timeout", 3000);
            verify(tracker).fireEventTrackingURL("https://example.test/outer?code=301");
            verify(tracker).fireEventTrackingURL("https://example.test/inner?code=301");
            verifyNoMoreInteractions(tracker);
            verify(mockListener).onResult(any());
        } finally {
            singleton.set(null, original);
        }
    }

    @Test
    public void cancelledExtractionIgnoresLateResponseAndFailure() throws Exception {
        vastParserExtractor.extract(wrapper("", "first"));
        ArgumentCaptor<org.prebid.mobile.rendering.networking.ResponseHandler> callback =
                ArgumentCaptor.forClass(org.prebid.mobile.rendering.networking.ResponseHandler.class);
        verify(mockAsyncVastLoader).loadVast(anyString(), callback.capture());
        vastParserExtractor.cancel();
        callback.getValue().onError("timeout", 3000);
        vastParserExtractor.extract(defaultResponseString);
        verifyNoInteractions(mockListener);
    }

    @Test
    public void whenFirstExtract_AssignRootParserAndMakeRequest()
    throws IllegalAccessException, IOException {
        String responseString = ResourceUtils.convertResourceToString("vast_wrapper_linear_nonlinear.xml");
        vastParserExtractor.extract(responseString);
        assertNotNull(WhiteBox.field(VastParserExtractor.class, "rootVastParser").get(vastParserExtractor));
        verify(mockAsyncVastLoader).loadVast(anyString(), any());
    }

    @Test
    public void extractAndRootParserNotNull_SetWrapperToLatestParser()
    throws IllegalAccessException {
        AdResponseParserVast mockParser = mock(AdResponseParserVast.class);
        WhiteBox.field(VastParserExtractor.class, "rootVastParser").set(vastParserExtractor, mockResponseParserVast);
        WhiteBox.field(VastParserExtractor.class, "latestVastWrapperParser").set(vastParserExtractor, mockParser);
        vastParserExtractor.extract(defaultResponseString);
        verify(mockParser).setWrapper(any(AdResponseParserVast.class));
    }

    @Test
    public void extractAndVastUrlIsEmpty_NotifyListener() {
        when(mockResponseParserVast.getVastUrl()).thenReturn("");
        vastParserExtractor.extract(defaultResponseString);
        verify(mockListener).onResult(any(VastExtractorResult.class));
    }

    @Test
    public void extractAndWrapperLimitReached_CallOnFailedToLoad()
    throws IllegalAccessException, IOException {
        String responseString = ResourceUtils.convertResourceToString("vast_wrapper_linear_nonlinear.xml");
        WhiteBox.field(VastParserExtractor.class, "vastWrapperCount").set(vastParserExtractor, 5);
        final AdException exception = new AdException(INTERNAL_ERROR, WRAPPER_LIMIT_REACH_ERROR.toString());

        vastParserExtractor.extract(responseString);

        ArgumentCaptor<VastExtractorResult> argument = ArgumentCaptor.forClass(VastExtractorResult.class);

        verify(mockListener).onResult(argument.capture());
        final VastExtractorResult value = argument.getValue();
        assertTrue(value.hasException());
        assertEquals(exception.getMessage(), value.getAdException().getMessage());
    }

    @Test
    public void disallowedAdditionalWrapperDoesNotFetchOrRender() {
        for (String value : new String[]{"false", "0"}) {
            VastParserExtractor.Listener listener = mock(VastParserExtractor.Listener.class);
            AsyncVastLoader loader = mock(AsyncVastLoader.class);
            VastParserExtractor extractor = new VastParserExtractor(listener);
            try {
                WhiteBox.field(VastParserExtractor.class, "asyncVastLoader").set(extractor, loader);
            } catch (IllegalAccessException error) {
                throw new AssertionError(error);
            }
            extractor.extract(wrapper(" followAdditionalWrappers=\"" + value + "\"", "first"));
            extractor.extract(wrapper("", "second"));
            verify(loader).loadVast(eq("https://example.test/first"), any());
            verifyNoMoreInteractions(loader);
            ArgumentCaptor<VastExtractorResult> result = ArgumentCaptor.forClass(VastExtractorResult.class);
            verify(listener).onResult(result.capture());
            assertTrue(result.getValue().hasException());
        }
    }

    @Test
    public void disallowedAdditionalWrapperStillAcceptsInline() {
        vastParserExtractor.extract(wrapper(" followAdditionalWrappers=\"false\"", "first"));
        vastParserExtractor.extract(defaultResponseString);
        ArgumentCaptor<VastExtractorResult> result = ArgumentCaptor.forClass(VastExtractorResult.class);
        verify(mockListener).onResult(result.capture());
        assertFalse(result.getValue().hasException());
    }

    @Test
    public void omittedWrapperRestrictionContinuesUnwrapping() {
        vastParserExtractor.extract(wrapper("", "first"));
        vastParserExtractor.extract(wrapper("", "second"));
        verify(mockAsyncVastLoader).loadVast(eq("https://example.test/first"), any());
        verify(mockAsyncVastLoader).loadVast(eq("https://example.test/second"), any());
        verifyNoInteractions(mockListener);
    }

    private static String wrapper(String attributes, String path) {
        return "<VAST version=\"3.0\"><Ad><Wrapper" + attributes + "><AdSystem>Fixture</AdSystem>"
            + "<VASTAdTagURI>https://example.test/" + path + "</VASTAdTagURI>"
            + "<Impression>https://example.test/impression</Impression><Creatives/></Wrapper></Ad></VAST>";
    }

    @Test
    public void cancel_CancelRunningTask() {
        vastParserExtractor.cancel();

        verify(mockAsyncVastLoader).cancelTask();
    }
}