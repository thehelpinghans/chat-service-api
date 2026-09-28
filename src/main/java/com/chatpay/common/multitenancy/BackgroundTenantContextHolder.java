package com.chatpay.common.multitenancy;

// HTTP 요청이 없는 실행 컨텍스트(스케줄러 등)에서 tenantId를 전파하기 위한 순수 ThreadLocal.
// TenantIdentifierResolver가 RequestContextHolder에서 못 찾을 때 폴백으로 확인한다.
// set/clear는 밖으로 노출하지 않고 open()이 리턴하는 Scope로만 짝을 맞춰 쓰게 해,
// try-with-resources로 닫는 걸 빠뜨리는 실수를 구조적으로 막는다.
public class BackgroundTenantContextHolder {

    private static final ThreadLocal<Long> holder = new ThreadLocal<>();

    private BackgroundTenantContextHolder() {
    }

    public static Long get() {
        return holder.get();
    }

    public static Scope open(Long tenantId) {
        holder.set(tenantId);
        return holder::remove;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
