package com.softzenith.crm.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedProxyFilterTest {

    private static final String SECRET = "proxy-secret-0123456789abcdef-0123";

    /** The address the controllers would see. */
    private static String remoteAddrSeen(TrustedProxyFilter filter, String secretHeader, String clientIpHeader) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/public/tenants/x/enquiries");
        request.setRemoteAddr("76.76.21.21"); // the website host
        if (secretHeader != null) request.addHeader(TrustedProxyFilter.SECRET, secretHeader);
        if (clientIpHeader != null) request.addHeader(TrustedProxyFilter.CLIENT_IP, clientIpHeader);
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest().getRemoteAddr();
    }

    @Test
    void theWebsiteWithTheSecretPassesTheVisitorsAddress() throws Exception {
        var filter = new TrustedProxyFilter(SECRET);
        assertThat(remoteAddrSeen(filter, SECRET, "49.36.10.7")).isEqualTo("49.36.10.7");
        assertThat(remoteAddrSeen(filter, SECRET, " 2401:4900:1c2a::5 ")).isEqualTo("2401:4900:1c2a::5");
    }

    @Test
    void withoutTheRightSecretTheHeaderIsIgnored() throws Exception {
        var filter = new TrustedProxyFilter(SECRET);
        assertThat(remoteAddrSeen(filter, null, "49.36.10.7")).isEqualTo("76.76.21.21");
        assertThat(remoteAddrSeen(filter, "guess", "49.36.10.7")).isEqualTo("76.76.21.21");
        assertThat(remoteAddrSeen(filter, SECRET, null)).isEqualTo("76.76.21.21");
        assertThat(remoteAddrSeen(new TrustedProxyFilter(""), SECRET, "49.36.10.7")).isEqualTo("76.76.21.21");
    }

    @Test
    void somethingThatIsNotAnAddressIsIgnored() throws Exception {
        var filter = new TrustedProxyFilter(SECRET);
        assertThat(remoteAddrSeen(filter, SECRET, "evil<script>")).isEqualTo("76.76.21.21");
        assertThat(remoteAddrSeen(filter, SECRET, "1.2.3.4, 5.6.7.8")).isEqualTo("76.76.21.21");
    }

    @Test
    void aShortSecretStopsTheApp() {
        assertThatThrownBy(() -> new TrustedProxyFilter("too-short")).hasMessageContaining("32");
    }
}
