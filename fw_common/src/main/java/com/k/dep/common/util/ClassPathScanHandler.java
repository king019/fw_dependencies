package com.k.dep.common.util;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 扫描指定包（包括jar）下的class文件 <br>
 * <a href="http://sjsky.iteye.com">http://sjsky.iteye.com</a>
 */
@NoArgsConstructor
@Data
public class ClassPathScanHandler {

    /**
     * 是否排除内部类 true->是 false->否
     */
    private boolean excludeInner = true;
    /**
     * 过滤规则适用情况 true—>搜索符合规则的 false->排除符合规则的
     */
    private boolean checkInOrEx = true;

    /**
     * 过滤规则列表 如果是null或者空，即全部符合不过滤
     */
    private List<String> classFilters = null;


    /**
     * excludeInner:是否排除内部类 true->是 false->否<br> checkInOrEx：过滤规则适用情况 true—>搜索符合规则的 false->排除符合规则的<br> classFilters：自定义过滤规则，如果是null或者空，即全部符合不过滤
     */
    public ClassPathScanHandler(Boolean excludeInner, Boolean checkInOrEx,
                                List<String> classFilters) {
        this.excludeInner = excludeInner;
        this.checkInOrEx = checkInOrEx;
        this.classFilters = classFilters;

    }

    public static Set<Class<?>> getPackageAllClasses(String basePackage) {
        return ClassPathScanHandler.getPackageAllClasses(basePackage, true, true, true, true);
    }

    public static Set<Class<?>> getPackageAllClasses(String basePackage, Set<Class> filters) {
        return ClassPathScanHandler.getPackageAllClasses(basePackage, true, true, true, true, filters);
    }

    public static Set<Class<?>> getPackageAllClasses(String basePackage, boolean recursive, boolean interfaceImpl, boolean filterAbstract, boolean filterNoPublic, Set<Class> filters) {
        Set<Class<?>> classes = ClassPathScanHandler.getPackageAllClasses(basePackage, recursive, interfaceImpl, filterAbstract, filterNoPublic);
        classes = classes.stream().filter(clazz -> filters.stream().anyMatch(filter -> filter.isAssignableFrom(clazz))).collect(Collectors.toSet());
        return classes;
    }

    public static Set<Class<?>> getPackageAllClasses(String basePackage, boolean recursive, boolean interfaceImpl, boolean filterAbstract, boolean filterNoPublic) {
        ClassPathScanHandler handler = new ClassPathScanHandler();
        Set<Class<?>> classes = handler.getPackageAllClasses(basePackage, recursive);
        return classes.stream().filter(classzz -> !interfaceImpl || filterInterface(classzz)).filter(classzz -> !filterAbstract || !filterAbstract(classzz)).filter(classzz -> !filterNoPublic || filterPublic(classzz)).collect(Collectors.toSet());
    }

    /**
     * 扫描包
     *
     * @param basePackage 基础包
     * @param recursive   是否递归搜索子包
     * @return Set
     */
    public Set<Class<?>> getPackageAllClasses(String basePackage, boolean recursive) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String packageName = basePackage;
        if (packageName.endsWith(".")) {
            packageName = packageName
                    .substring(0, packageName.lastIndexOf('.'));
        }
        String package2Path = packageName.replace('.', '/');

        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(
                    package2Path);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    //("扫描file类型的class文件....");
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    doScanPackageClassesByFile(classes, packageName, filePath, recursive);
                } else if ("jar".equals(protocol)) {
                    //("扫描jar文件中的类....");
                    doScanPackageClassesByJar(packageName, url, recursive,
                            classes);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }

        return classes;
    }

    /**
     * 以jar的方式扫描包下的所有Class文件<br>
     *
     * @param basePackage eg：michael.utils.
     */
    private void doScanPackageClassesByJar(String basePackage, URL url,
                                           boolean recursive, Set<Class<?>> classes) {
        String packageName = basePackage;
        String package2Path = packageName.replace('.', '/');
        JarFile jar;
        try {
            jar = ((JarURLConnection) url.openConnection()).getJarFile();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(package2Path) || entry.isDirectory()) {
                    continue;
                }

                // 判断是否递归搜索子包
                if (!recursive
                        && name.lastIndexOf('/') != package2Path.length()) {
                    continue;
                }
                // 判断是否过滤 inner class
                if (excludeInner && name.indexOf('$') != -1) {
                    continue;
                }
                String classSimpleName = name
                        .substring(name.lastIndexOf('/') + 1);
                // 判定是否符合过滤条件
                if (filterClassName(classSimpleName)) {
                    String className = name.replace('/', '.');
                    className = className.substring(0, className.length() - 6);
                    try {
                        classes.add(Thread.currentThread()
                                .getContextClassLoader().loadClass(className));
                    } catch (Throwable e) {
                        //e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
        }
    }

    /**
     * 以文件的方式扫描包下的所有Class文件
     */
    private void doScanPackageClassesByFile(Set<Class<?>> classes,
                                            String packageName, String packagePath, boolean recursive) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        boolean fileRecursive = recursive;
        File[] dirfiles = dir.listFiles(new FileFilter() {
            // 自定义文件过滤规则
            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return fileRecursive;
                }
                String filename = file.getName();
                if (excludeInner && filename.indexOf('$') != -1) {
                    return false;
                }
                return filterClassName(filename);
            }
        });
        for (File file : dirfiles) {
            if (file.isDirectory()) {
                doScanPackageClassesByFile(classes, packageName + "."
                        + file.getName(), file.getAbsolutePath(), recursive);
            } else {
                String className = file.getName().substring(0,
                        file.getName().length() - 6);
                try {
                    classes.add(Thread.currentThread().getContextClassLoader()
                            .loadClass(packageName + '.' + className));

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 根据过滤规则判断类名
     */
    private boolean filterClassName(String className) {
        if (!className.endsWith(".class")) {
            return false;
        }
        if (null == classFilters || classFilters.isEmpty()) {
            return true;
        }
        String tmpName = className.substring(0, className.length() - 6);
        boolean flag = false;
        for (String str : classFilters) {
            String tmpreg = "^" + str.replace("*", ".*") + "$";
            Pattern p = Pattern.compile(tmpreg);
            if (p.matcher(tmpName).find()) {
                flag = true;
                break;
            }
        }
        return (checkInOrEx && flag) || (!checkInOrEx && !flag);
    }

    private static boolean filterInterface(Class clazz) {
        Class[] interfaces = clazz.getInterfaces();
        return interfaces.length > 0;
    }

    private static boolean filterAbstract(Class clazz) {
        return Modifier.isAbstract(clazz.getModifiers());
    }


    private static boolean filterPublic(Class clazz) {
        return Modifier.isPublic(clazz.getModifiers());
    }

    /**
     *
     */
    public static void main(String[] args) {

        // 自定义过滤规则
        List<String> classFilters = new ArrayList<>();
        classFilters.add("File*");

        // 创建一个扫描处理器，排除内部类 扫描符合条件的类
        ClassPathScanHandler handler = new ClassPathScanHandler(true, true,
                classFilters);

        System.out
                .println("开始递归扫描jar文件的包：org.apache.commons.io 下符合自定义过滤规则的类...");
        Set<Class<?>> calssList = handler.getPackageAllClasses(
                "org.apache.commons.io", true);
        for (Class<?> cla : calssList) {
            //log.info(cla.getName());
        }
        //log.info("开始递归扫描file文件的包：michael.hessian 下符合自定义过滤规则的类...");
        classFilters.clear();
        classFilters.add("Hessian*");
        calssList = handler.getPackageAllClasses("michael.hessian", true);
        for (Class<?> cla : calssList) {
            //log.info(cla.getName());
        }
    }
}