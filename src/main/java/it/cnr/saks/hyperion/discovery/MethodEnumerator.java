package it.cnr.saks.hyperion.discovery;

import it.cnr.saks.hyperion.symbolic.AnalyzerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

public class MethodEnumerator implements Iterable<MethodDescriptor> {
    private static final Logger log = LoggerFactory.getLogger(MethodEnumerator.class);
    private final List<MethodDescriptor> methods = new ArrayList<>();
    private final Hashtable<String, ArrayList<MethodDescriptor>> beforeMethods = new Hashtable<>();
    DiscoveryConfiguration discoveryConfiguration;

    public MethodEnumerator(DiscoveryConfiguration discoveryConfiguration) throws IOException, AnalyzerException {
        this.discoveryConfiguration = discoveryConfiguration;
        List<Class> classes = this.enumerateClasses(discoveryConfiguration.getTestPrograms());

        for (Class klass: classes) {
            if(Modifier.isAbstract(klass.getModifiers())) {
                log.info("Analysing class {}: skipping, it's an abstract class.", klass.getName());
                continue;
            }

            log.info("Analysing class {}: retrieving valid methods...", klass.getName());
            Method[] methods = this.getAccessibleMethods(klass);
            for(Method currentMethod: methods) {
                boolean isTest = false;
                boolean isBefore = false;

                if(!currentMethod.getDeclaringClass().getName().equals(klass.getName()))
                    continue;

                for(Annotation ann: currentMethod.getAnnotations()) {
                    if(ann.toString().equals("@org.junit.Before()")) {
                        isBefore = true;
                        break;
                    }
                    if(ann.toString().contains("@org.junit.Test")) {
                        isTest = true;
                    }
                    if(ann.toString().contains("@org.junit.Ignore")) {
                        isTest = false;
                        break;
                    }
                }

                if(isBefore) {
                    if(!beforeMethods.containsKey(klass.getName())) {
                        ArrayList<MethodDescriptor> beforeMethods = new ArrayList<>();
                        beforeMethods.add(new MethodDescriptor(currentMethod.getName(), this.getMethodDescriptor(currentMethod), klass.getName()));
                        this.beforeMethods.put(klass.getName(), beforeMethods);
                    } else {
                        beforeMethods.get(klass.getName()).add(new MethodDescriptor(currentMethod.getName(), this.getMethodDescriptor(currentMethod), klass.getName()));
                    }
                    continue;
                }

                if(!isTest)
                    continue;

                this.methods.add(new MethodDescriptor(currentMethod.getName(), this.getMethodDescriptor(currentMethod), klass.getName()));
            }
        }

        // Refine the list of methods to analyze
        this.methods.removeIf(method -> discoveryConfiguration.getExcludeTest().contains(method.getMethodName()));
        if(discoveryConfiguration.getIncludeTest().size() > 0)
            this.methods.removeIf(method -> !discoveryConfiguration.getIncludeTest().contains(method.getMethodName()));
    }

    @Override
    public Iterator<MethodDescriptor> iterator() {
        return this.methods.iterator();
    }
    
    public List<MethodDescriptor> getBeforeMethods(String klass) {
        return this.beforeMethods.get(klass);
    }

    private String getMethodDescriptor(Method m)
    {
        StringBuilder s= new StringBuilder("(");
        for(final Class klass:(m.getParameterTypes()))
            s.append(this.getDescriptorForClass(klass));
        s.append(')');
        return s + this.getDescriptorForClass(m.getReturnType());
    }

    private String getDescriptorForClass(final Class klass)
    {
        if(klass.isPrimitive())
        {
            if(klass==byte.class)
                return "B";
            if(klass==char.class)
                return "C";
            if(klass==double.class)
                return "D";
            if(klass==float.class)
                return "F";
            if(klass==int.class)
                return "I";
            if(klass==long.class)
                return "J";
            if(klass==short.class)
                return "S";
            if(klass==boolean.class)
                return "Z";
            if(klass==void.class)
                return "V";
            throw new RuntimeException("Unrecognized primitive "+klass);
        }
        if(klass.isArray()) return klass.getName().replace('.', '/');
        return ('L'+klass.getName()+';').replace('.', '/');
    }

    public static Method[] getAccessibleMethods(Class klass)
    {
        List<Method> result = new ArrayList<>();
        while (klass != null) {
            for (Method method: klass.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                    result.add(method);
                }
            }
            klass = klass.getSuperclass();
        }
        return result.toArray(new Method[0]);
    }


    private List<Class> enumerateClasses(List<String> classPaths) throws IOException {
        List<String> paths = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        log.info("Loading classes...");
        for(String classPath: classPaths) {
            Files.find(Paths.get(classPath),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().endsWith(".class"))
                    .forEach(pathVal -> paths.add(pathVal.toString()));

            for (String classFile : paths) {
                try {
                    classes.add(loadClass(classFile, classPath, this.discoveryConfiguration.getClassPath()));
                } catch (AnalyzerException | URISyntaxException ignored) {}
            }
        }

        return classes;
    }

    private Class loadClass(String classFile, String path, URL[] urls) throws AnalyzerException, URISyntaxException {
        path = new URI(path).normalize().toString();
	    String classFilePkg = classFile.replace(path, "");
        String classPkg = classFilePkg.substring(0, classFilePkg.lastIndexOf('.')).replace(path, "").replace(File.separator, ".");

        ClassLoader cl;
        Class<?> dynamicClass;

        try {
            cl = new URLClassLoader(urls);
            log.info("Loading {}...", classPkg);
            dynamicClass = cl.loadClass(classPkg);

            try {
                Class.forName(dynamicClass.getName(), true, dynamicClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                log.error("Class {} not found in path {}", classFile, path);
                throw new AssertionError(e);  // Can't happen
            }

        } catch (ClassNotFoundException e) {
	        log.error("Class {} not found in path {}", classFile, path);
            throw new AnalyzerException("Unable to find class " + e.getMessage());
        }

        return dynamicClass;
    }

    public int getMethodsCount() {
        return this.methods.size();
    }

    public List<MethodDescriptor> getMethods() {
        return methods;
    }
}
