package com.asg.fabricerp.profile;

import com.asg.fabricerp.accounts.CostCentreRepository;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.RoleRepository;
import com.asg.fabricerp.security.AccessLogService;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserDetailsService;
import com.asg.fabricerp.security.FabricUserPrincipal;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** My profile through the web layer: the page, the header avatar, the photo's caching, and who may do what. */
@WebMvcTest(controllers = ProfileController.class)
@Import(SecurityConfig.class)
class ProfileWebTest {

    private static final Long ORG = 1L;

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private ProfileService profiles;
    // What the layout reads for the header.
    @MockitoBean private RoleRepository roleRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrganizationRepository organizations;
    @MockitoBean private CostCentreRepository costCentres;

    private FabricUser asha;

    @BeforeEach
    void setUp() {
        asha = new FabricUser("asha", "hash", 10L, "AF");
        asha.setId(7L);
        asha.setOrganizationId(ORG);
        asha.setFullName("Asha Rahman");
        asha.setUnrestricted(true);
        when(userDetailsService.reload(eq("asha"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(asha)));
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        when(profiles.profile(7L)).thenAnswer(i -> profile());
    }

    @Test
    void thePageRendersInTheLayout_withInitialsUntilThereIsAPhoto() throws Exception {
        mvc.perform(get("/account/profile").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("My profile")))
            .andExpect(content().string(containsString("Asha Rahman")))
            .andExpect(content().string(containsString("Merchandiser · Marketing")))
            .andExpect(content().string(containsString(">AR<")))              // header avatar: initials
            .andExpect(content().string(containsString("href=\"/account/profile\"")))
            .andExpect(content().string(containsString("/js/profile.js")));
    }

    @Test
    void withAPhoto_theHeaderShowsTheVersionedThumbnail() throws Exception {
        asha.setPhotoVersion(1234L);

        mvc.perform(get("/account/profile").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("src=\"/account/photo/7?size=thumb&amp;v=1234\"")));
    }

    @Test
    void visitorsAreSentToSignIn_andTheApiRefusesThem() throws Exception {
        mvc.perform(get("/account/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/account/photo/7")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/account/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void savingUpdatesOnlyTheCallersOwnProfile() throws Exception {
        mvc.perform(post("/api/account/profile").with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha R.\",\"email\":\"asha@example.com\",\"id\":99}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("asha"));

        verify(profiles).update(eq(7L), argThat(r -> "Asha R.".equals(r.fullName()) && "asha@example.com".equals(r.email())));
    }

    @Test
    void aRefusedValueComesBackAsTheServicesSentence() throws Exception {
        doThrow(new IllegalArgumentException("That email address does not look right")).when(profiles).update(eq(7L), any());

        mvc.perform(post("/api/account/profile").with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Asha\",\"email\":\"nope\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("That email address does not look right"));
    }

    @Test
    void uploadingAPhotoSendsItsBytesToTheService() throws Exception {
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        mvc.perform(multipart("/api/account/profile/photo")
                .file(new MockMultipartFile("photo", "me.jpg", "image/jpeg", bytes))
                .with(signedIn()).with(csrf()))
            .andExpect(status().isOk());

        verify(profiles).setPhoto(eq(7L), aryEq(bytes));
    }

    @Test
    void aBadPictureIsA400WithTheReason() throws Exception {
        when(profiles.setPhoto(eq(7L), any())).thenThrow(new IllegalArgumentException("That file is not a picture this can read"));

        mvc.perform(multipart("/api/account/profile/photo")
                .file(new MockMultipartFile("photo", "x.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                .with(signedIn()).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("not a picture")));
    }

    @Test
    void theCurrentVersionIsCachedForAYear_anOldOneIsRevalidated_andAnotherOrgsIsNotFound() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9};
        when(profiles.photo(eq(7L), eq(true), eq(Set.of(ORG)))).thenReturn(Optional.of(new ProfileService.StoredPhoto(jpeg, 1234L)));

        mvc.perform(get("/account/photo/7").param("size", "thumb").param("v", "1234").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
            .andExpect(header().string("Cache-Control", containsString("immutable")))
            .andExpect(header().string("ETag", "\"1234-t\""))
            .andExpect(content().bytes(jpeg));

        mvc.perform(get("/account/photo/7").param("size", "thumb").param("v", "1").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-cache")));

        mvc.perform(get("/account/photo/7").param("size", "thumb").header("If-None-Match", "\"1234-t\"").with(signedIn()))
            .andExpect(status().isNotModified());

        mvc.perform(get("/account/photo/8").with(signedIn())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------ fixtures

    private Map<String, Object> profile() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", 7L);
        p.put("username", "asha");
        p.put("fullName", asha.getFullName());
        p.put("designation", "Merchandiser");
        p.put("department", "Marketing");
        p.put("headline", "Merchandiser · Marketing");
        p.put("photoUrl", null);
        p.put("photo", null);
        p.put("roles", List.of("Marketing"));
        p.put("unrestricted", true);
        p.put("completeness", 50);
        Map<String, Object> home = new HashMap<>();
        home.put("organization", "Amanat Shah Fabrics");
        home.put("businessUnit", "Weaving Unit");
        home.put("store", null);
        p.put("home", home);
        p.put("email", null);
        p.put("phone", null);
        p.put("bio", null);
        p.put("photoVersion", null);
        p.put("lastLoginAt", null);
        p.put("passwordChangedAt", null);
        p.put("memberSince", null);
        p.put("recentSignIns", List.of());
        return p;
    }

    private RequestPostProcessor signedIn() {
        var principal = new FabricUserPrincipal(asha);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
