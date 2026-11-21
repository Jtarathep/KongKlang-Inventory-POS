package com.kongklang.service;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Bounded, single-instance throttle. Use upstream rate limiting for a clustered deployment. */
@Component
public class LoginThrottle {
    private static final int LIMIT=10,MAX_KEYS=10_000;
    private static final long WINDOW=Duration.ofMinutes(10).toMillis();
    private record Window(long ends,int count){}
    private final LinkedHashMap<String,Window> attempts=new LinkedHashMap<>(16,.75f,true);
    public synchronized boolean allow(String remote,String username){
        long now=System.currentTimeMillis();String account="account:"+username.toLowerCase(Locale.ROOT),ip="remote:"+remote;
        if(blocked(account,now,LIMIT)||blocked(ip,now,3*LIMIT))return false;
        charge(account,now);charge(ip,now);return true;
    }
    public synchronized void success(String username){attempts.remove("account:"+username.toLowerCase(Locale.ROOT));}
    private boolean blocked(String key,long now,int limit){Window w=attempts.get(key);return w!=null&&w.ends()>now&&w.count()>=limit;}
    private void charge(String key,long now){Window w=attempts.get(key);attempts.put(key,w==null||w.ends()<=now?new Window(now+WINDOW,1):new Window(w.ends(),w.count()+1));while(attempts.size()>MAX_KEYS)attempts.pollFirstEntry();}
}
