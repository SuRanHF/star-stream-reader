package com.huazhenghai.readergame.security;

/**
 * 当前登录用户上下文 (ThreadLocal).
 * <p>
 * 在 {@link JwtAuthInterceptor} 的 preHandle 中被设置,
 * 在 afterCompletion 中被清除.
 * <br>
 * Controller / Service 层通过 {@code LoginUserContext.get()} 获取当前用户.
 * <br>
 * 注意: 由于使用 ThreadLocal, 必须保证 <b>同一线程内</b> 的调用才是有效的.
 * </p>
 */
public final class LoginUserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginUserContext() {
        // 工具类, 禁止实例化
    }

    /**
     * 获取当前线程的登录用户.
     *
     * @return 登录用户信息, 未登录时返回 null
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 设置当前线程的登录用户.
     *
     * @param user 登录用户
     */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /**
     * 清除当前线程的登录用户 (防止内存泄漏).
     */
    public static void clear() {
        HOLDER.remove();
    }
}
