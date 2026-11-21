package com.kongklang.service;
import java.util.Set;

/** Explicit route policy; central administrators have no shop transaction access. */
public final class RolePolicy {
    private RolePolicy() {}
    private static boolean under(String p,String root) {return p.equals(root)||p.startsWith(root+"/");}
    public static boolean allows(String role,String method,String path) {
        if(role==null)return false;
        boolean read=Set.of("GET","HEAD","OPTIONS").contains(method);
        if(path.equals("/logout"))return !read;
        if(path.equals("/settings/password"))return Set.of("SYSTEM_ADMIN","OWNER","ADMIN","STAFF","SALES","WAREHOUSE","PURCHASING","ACCOUNTING").contains(role);
        if(role.equals("SYSTEM_ADMIN"))return under(path,"/management");
        if(!Set.of("OWNER","ADMIN","STAFF","SALES","WAREHOUSE","PURCHASING","ACCOUNTING").contains(role))return false;
        boolean admin=Set.of("OWNER","ADMIN").contains(role);
        if(path.equals("/kk-ai"))return read&&(admin||role.equals("ACCOUNTING"));
        if(path.equals("/api/kk-ai/ask"))return method.equals("POST")&&(admin||role.equals("ACCOUNTING"));
        if(under(path,"/mobile"))return read;
        if(under(path,"/api/mobile"))return read||(path.equals("/api/mobile/receive")&&(admin||role.equals("WAREHOUSE")));
        if(under(path,"/settings/tax"))return admin;
        if(under(path,"/management"))return admin&&under(path,"/management/users");
        if(under(path,"/reports")||under(path,"/api/reports")||under(path,"/accounting")||under(path,"/api/accounting"))return admin||role.equals("ACCOUNTING");
        if(under(path,"/purchasing")||under(path,"/api/purchasing"))return admin||Set.of("PURCHASING","WAREHOUSE","ACCOUNTING").contains(role);
        if(under(path,"/sales")||under(path,"/api/sales"))return admin||Set.of("SALES","STAFF").contains(role)||(read&&role.equals("ACCOUNTING"));
        if(under(path,"/stock/legacy-review")||under(path,"/api/stock/legacy-review"))return admin;
        if(under(path,"/stock")||under(path,"/api/stock"))return read||admin||role.equals("WAREHOUSE");
        if(under(path,"/products")||under(path,"/api/products")||under(path,"/categories")||under(path,"/api/categories")) {
            if(path.startsWith("/products/delete/"))return !read&&admin;
            if(path.equals("/products/new")||path.startsWith("/products/edit/"))return admin;
            return read||admin;
        }
        return read&&(path.equals("/")||under(path,"/api/dashboard"));
    }
}
