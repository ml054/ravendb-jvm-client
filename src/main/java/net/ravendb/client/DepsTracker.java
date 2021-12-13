package net.ravendb.client;

import net.ravendb.client.documents.session.DocumentSession;
import net.ravendb.client.documents.session.IAdvancedSessionOperations;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DepsTracker {

    public static DepsTracker INSTANCE = new DepsTracker();

    public static String currentTest;

    private Set<String> events = new HashSet<>();

    public void testStart(String className, String methodName) {
        currentTest = className + "::" + methodName;
    }

    public void reportEvent(String name) {
        events.add(name);
    }

    public <T> T track(T target) {
        ProxyFactory pf = new ProxyFactory();
        pf.setTarget(target);
        pf.addAdvice((MethodInterceptor) mi -> {
            reportEvent(target.getClass().getSimpleName() + "::" + mi.getMethod().getName());
            try {
                return mi.getMethod().invoke(mi.getThis(), mi.getArguments());
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        });
        return (T) pf.getProxy();
    }

    public void testEnd() {
        try (FileWriter fw = new FileWriter("c:\\temp\\log.txt", true)) {
            fw.write(currentTest + " --> " + String.join(";", events));
            fw.write(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
        events.clear();
    }
}
