package com.sintinium.oauth.login;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import com.mojang.authlib.Agent;
import com.mojang.authlib.UserType;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import com.mojang.util.UUIDTypeAdapter;
import cpw.mods.fml.relauncher.ReflectionHelper;
import org.apache.http.HttpHost;
import org.apache.http.client.protocol.HttpClientContext;

public class LoginUtil {

    public static String lastMojangUsername = null;
    public static boolean needsRefresh = true;
    public static boolean wasOnline = false;
    private static long lastCheck = -1L;

    private static YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(
            Minecraft.getMinecraft().getProxy(),
            UUID.randomUUID().toString());
    private static YggdrasilUserAuthentication userAuth = (YggdrasilUserAuthentication) authService
            .createUserAuthentication(Agent.MINECRAFT);
    private static YggdrasilMinecraftSessionService minecraftSessionService = (YggdrasilMinecraftSessionService) authService
            .createMinecraftSessionService();

    public static void updateOnlineStatus() {
        needsRefresh = true;
        isOnline();
    }

    public static HttpClientContext getHttpRequestContext() {
        Proxy proxy = Minecraft.getMinecraft().getProxy();
        if (proxy.type() == Proxy.Type.DIRECT)
            return null;
        HttpClientContext ctx = new HttpClientContext();
        ctx.setAttribute("socks.address", proxy.address());
        return ctx;
    }

    public static boolean isOnline() {
        if (!needsRefresh && System.currentTimeMillis() - lastCheck < 1000 * 10) {
            return wasOnline;
        }
        Session session = Minecraft.getMinecraft().getSession();
        String uuid = UUID.randomUUID().toString();
        needsRefresh = false;
        lastCheck = System.currentTimeMillis();
        try {
            minecraftSessionService.joinServer(session.func_148256_e(), session.getToken(), uuid);
            if (minecraftSessionService.hasJoinedServer(session.func_148256_e(), uuid).isComplete()) {
                wasOnline = true;
                return true;
            } else {
                wasOnline = false;
                return false;
            }
        } catch (AuthenticationException e) {
            wasOnline = false;
            return false;
        }
    }

    public static void loginMs(MicrosoftLogin.MinecraftProfile profile) {
        Session session = new Session(profile.name, profile.id, profile.token.accessToken, Session.Type.MOJANG.name());
        setSession(session);
    }

    public static Optional<Boolean> loginMojangOrLegacy(String username, String password) {
        try {
            if (password.isEmpty()) {
                Session session = new Session(
                        username,
                        UUID.nameUUIDFromBytes(username.getBytes()).toString(),
                        null,
                        UserType.LEGACY.getName());
                setSession(session);
                return Optional.of(true);
            }
            userAuth.setUsername(username);
            userAuth.setPassword(password);
            userAuth.logIn();

            String name = userAuth.getSelectedProfile().getName();
            String uuid = UUIDTypeAdapter.fromUUID(userAuth.getSelectedProfile().getId());
            String token = userAuth.getAuthenticatedToken();
            String type = userAuth.getUserType().getName();
            userAuth.logOut();

            Session session = new Session(name, uuid, token, type);
            setSession(session);
            lastMojangUsername = username;
            return Optional.of(true);
        } catch (AuthenticationUnavailableException e) {
            return Optional.empty();
        } catch (AuthenticationException e) {
            return Optional.of(false);
        }
    }

    private static void setSession(Session session) {
        needsRefresh = true;
        updateOnlineStatus();
        Field field = ReflectionHelper.findField(Minecraft.class, "field_71449_j", "session");
        field.setAccessible(true);
        try {
            field.set(Minecraft.getMinecraft(), session);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
