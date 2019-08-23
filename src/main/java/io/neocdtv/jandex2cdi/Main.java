package io.neocdtv.jandex2cdi;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Main {
  public final static Set<String> SESSION_BEANS = new HashSet<>();
  public final static Set<String> CDI_SCOPED_BEANS = new HashSet<>();
  public final static Set<String> CDI_INJECTION_TARGETS = new HashSet<>();
  public final static Set<String> CDI_INJECTION_POINTS = new HashSet<>();
  public final static Set<String> CDI_PRODUCER_BEANS = new HashSet<>();
  public final static Set<String> CDI_INJECTION_POINTS_INSTANCE = new HashSet<>();
  public final static Set<String> INTERCEPTORS = new HashSet<>();
  public final static Set<String> DECORATORS = new HashSet<>();
  public final static Set<String> JAX_RS_BEANS = new HashSet<>();
  public final static List<String> SESSION_BEANS_ANNOTATIONS = Arrays.asList(
      "javax.ejb.Stateless",
      "javax.ejb.Singleton",
      "javax.ejb.Stateful");
  public final static List<String> CDI_SCOPED_BEANS_ANNOTATIONS = Arrays.asList(
      "javax.enterprise.context.ApplicationScoped",
      "javax.enterprise.context.ConversationScoped",
      "javax.enterprise.context.Dependent",
      "javax.enterprise.context.RequestScoped",
      "javax.enterprise.context.SessionScoped");
  public final static String CDI_PRODUCES_ANNOTATION = "javax.enterprise.inject.Produces";

  private static final Set<String> MANUALLY_REMOVED_FROM_EXCLUSION = new HashSet<>();
  public static final String CDI_QUALIFIER_ANNOTATION = "javax.inject.Qualifier";
  public final static List<String> JAXRS_ANNOTATTIONS = Arrays.asList(
      "javax.ws.rs.Path",
      "javax.ws.rs.ext.Provider");

  static {
  }

  // TODO: inner classes
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      System.exit(1);
    }
    final String jandexPath = args[0];
    FileInputStream input = new FileInputStream(jandexPath);
    IndexReader reader = new IndexReader(input);
    Index index = reader.read();

    printClasses(index);
    findEJBs(index);
    findCDIScopedBeans(index);
    findJAXRSBeans(index);
    findInjection(index);
    findInterceptors(index);
    findDecorators(index);
    findProducers(index);
    findQualifierAnnotated(index);
    calculateScanWeldInclude(index);
    calculateScanExclude(index);
    // update beans.xml
    // - parse beans.xml
    // - if present remove scan node
    // - add new scan node
  }

  private static void usage() {
    System.out.println("usage todo");
  }

  // TODO: what todo with this one
  private static void findQualifierAnnotated(final Index index) {
    System.out.println("QUALIFIER ANNOTATED: ");
    DotName annotation = DotName.createSimple(CDI_QUALIFIER_ANNOTATION);
    final List<AnnotationInstance> annotations = index.getAnnotations(annotation);
    for (AnnotationInstance annotationInstance : annotations) {
      final AnnotationTarget.Kind kind = annotationInstance.target().kind();
      switch (kind) {
        case CLASS:
          final ClassInfo classInfo = annotationInstance.target().asClass();
          break;
        default:
          System.out.println("not expecting this");
          break;
      }
    }
    System.out.println("");
  }

  private static void calculateScanWeldInclude(Index index) {
    final Set<String> includedInScanning = new HashSet<>();
    includedInScanning.addAll(SESSION_BEANS);
    includedInScanning.addAll(CDI_SCOPED_BEANS);
    includedInScanning.addAll(CDI_INJECTION_TARGETS);
    includedInScanning.addAll(CDI_INJECTION_POINTS);
    includedInScanning.addAll(JAX_RS_BEANS);
    includedInScanning.addAll(INTERCEPTORS);
    includedInScanning.addAll(DECORATORS);
    includedInScanning.addAll(CDI_PRODUCER_BEANS);
    includedInScanning.addAll(CDI_INJECTION_POINTS_INSTANCE);

    StringBuilder snippet = new StringBuilder();
    snippet.append("<weld:scan>\n");
    final String template = "  <weld:include name=\"%s\" />";
    final TreeSet<String> sorted = new TreeSet<>(includedInScanning);

    for (String entry : sorted) {
      snippet.append(String.format(template, entry));
      snippet.append("\n");
    }
    snippet.append("</weld:scan>\n");
    System.out.println(snippet.toString());
  }

  private static void printClasses(Index index) {
    final Set<String> classes = index.getKnownClasses().stream().map(classInfo -> classInfo.toString()).collect(Collectors.toSet());
    System.out.println("CLASSES: ");
    for (String clazz : classes) {
      System.out.println("  CLASS: " + clazz);
    }
  }

  private static void findProducers(final Index index) {
    DotName inject = DotName.createSimple(CDI_PRODUCES_ANNOTATION);
    final List<AnnotationInstance> annotations = index.getAnnotations(inject);
    for (AnnotationInstance annotation : annotations) {
      final AnnotationTarget target = annotation.target();
      switch (target.kind()) {
        case FIELD:
          CDI_PRODUCER_BEANS.add(target.asField().declaringClass().toString());
          break;
        case METHOD:
          CDI_PRODUCER_BEANS.add(target.asMethod().declaringClass().toString());
          final MethodInfo methodInfo = target.asMethod();
          final Set<String> collect = methodInfo.parameters().stream().map(type -> type.toString()).collect(Collectors.toSet());
          for (String param : collect) {
            System.out.println("  CDI PRODUCER METHOD PARAM: " + param);
            CDI_INJECTION_POINTS.add(param);
            final Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementors(DotName.createSimple(param));
            for (ClassInfo allKnownImplementor : allKnownImplementors) {
              final String subTypeName = allKnownImplementor.toString();
              CDI_INJECTION_POINTS.add(subTypeName);
              System.out.println("  CDI PRODUCER METHOD PARAM (impl): " + subTypeName);
            }
          }
          break;
      }
    }
  }

  private static void calculateScanExclude(final Index index) {
    final Set<String> excludedFromScanning = index.getKnownClasses().stream().map(classInfo -> classInfo.toString()).collect(Collectors.toSet());
    excludedFromScanning.removeAll(SESSION_BEANS);
    excludedFromScanning.removeAll(CDI_SCOPED_BEANS);
    excludedFromScanning.removeAll(CDI_INJECTION_TARGETS);
    excludedFromScanning.removeAll(CDI_INJECTION_POINTS);
    excludedFromScanning.removeAll(JAX_RS_BEANS);
    excludedFromScanning.removeAll(INTERCEPTORS);
    excludedFromScanning.removeAll(DECORATORS);
    excludedFromScanning.removeAll(CDI_PRODUCER_BEANS);
    excludedFromScanning.removeAll(CDI_INJECTION_POINTS_INSTANCE);
    StringBuilder snippet = new StringBuilder();
    snippet.append("<scan>\n");
    final String template = "  <exclude name=\"%s\" />";

    final TreeSet<String> sorted = new TreeSet<>(excludedFromScanning);
    for (String entry : sorted) {
      if (!MANUALLY_REMOVED_FROM_EXCLUSION.contains(entry) && !isPackageInfo(entry)) {
        snippet.append(String.format(template, entry));
        snippet.append("\n");
      }
    }
    snippet.append("</scan>\n");
    System.out.println(snippet.toString());
  }

  // package-info classes break weld deployment
  private static boolean isPackageInfo(String entry) {
    return entry.endsWith("package-info");
  }

  public static void findEJBs(final Index index) {
    System.out.println("SESSION BEANS: ");
    for (String annotations : SESSION_BEANS_ANNOTATIONS) {
      DotName annotation = DotName.createSimple(annotations);
      for (AnnotationInstance annotationInstance : index.getAnnotations(annotation)) {
        final String name = annotationInstance.target().toString();
        SESSION_BEANS.add(name);
        System.out.println("  SESSION BEAN: " + name);
      }
    }
  }

  public static void findJAXRSBeans(final Index index) {
    System.out.println("JAXRS BEANS: ");
    for (String annotations : JAXRS_ANNOTATTIONS) {
      DotName pathAnno = DotName.createSimple(annotations);
      for (AnnotationInstance annotation : index.getAnnotations(pathAnno)) {
        final AnnotationTarget target = annotation.target();
        switch (annotation.target().kind()) {
          case CLASS:
            final String name = target.toString();
            JAX_RS_BEANS.add(name);
            System.out.println(" JAXRS BEAN: " + name);
            break;
        }
      }
    }
  }

  public static void findCDIScopedBeans(final Index index) {
    System.out.println("CDI SCOPED BEANS: ");
    for (String annotations : CDI_SCOPED_BEANS_ANNOTATIONS) {
      DotName annotation = DotName.createSimple(annotations);
      for (AnnotationInstance annotationInstance : index.getAnnotations(annotation)) {
        final AnnotationTarget target = annotationInstance.target();
        switch (target.kind()) {
          case CLASS:
            final String name = annotationInstance.target().toString();
            CDI_SCOPED_BEANS.add(name);
            System.out.println("  CDI SCOPED BEAN: " + name);
            break;
        }
      }
    }
    // add scopes from WeldDeployer.java
  }

  public static void findInjection(final Index index) {
    System.out.println("CDI INJECTIONS: ");
    DotName inject = DotName.createSimple("javax.inject.Inject");
    final List<AnnotationInstance> annotations = index.getAnnotations(inject);
    for (AnnotationInstance annotation : annotations) {
      final AnnotationTarget target = annotation.target();
      switch (target.kind()) {
        case FIELD:
          final FieldInfo fieldInfo = target.asField();
          final ClassInfo injectionTarget = fieldInfo.declaringClass();
          final String targetName = injectionTarget.toString();
          CDI_INJECTION_TARGETS.add(targetName);
          System.out.println("  CDI INJECTION TARGET: " + targetName);
          final Type type = fieldInfo.type();
          switch (type.kind()) {
            case PARAMETERIZED_TYPE:
              // expecting javax.enterprise.inject.Instance
              if (fieldInfo.type().asParameterizedType().name().toString().equals("javax.enterprise.inject.Instance")) {
                // does it also work with subclasses or only with interfaces?
                final Type interfaceType = fieldInfo.type().asParameterizedType().arguments().get(0);
                final DotName simple = DotName.createSimple(interfaceType.toString());
                final Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementors(simple);
                final Set<String> interfaceRealizations = allKnownImplementors.stream().map(classInfo -> classInfo.toString()).collect(Collectors.toSet());
                CDI_INJECTION_POINTS_INSTANCE.add(interfaceType.toString());
                CDI_INJECTION_POINTS_INSTANCE.addAll(interfaceRealizations);
              } else {
                System.out.println("not really expecting other stuff than javax.enterprise.inject.Instance, we need to investigate dear Watson");
              }
              break;
            default:
              final String pointName = type.toString();
              CDI_INJECTION_POINTS.add(pointName);
              System.out.println("  CDI INJECTION POINT: " + pointName);
              if (pointName.contains("Reader")) {
                System.out.println();
              }
              final Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementors(DotName.createSimple(pointName));
              for (ClassInfo allKnownImplementor : allKnownImplementors) {
                final String subTypeName = allKnownImplementor.toString();
                CDI_INJECTION_POINTS.add(subTypeName);
                System.out.println("  CDI INJECTION POINT (impl): " + subTypeName);
              }
              break;
          }
          break;
      }
    }
  }

  public static void findInterceptors(final Index index) {
    System.out.println("INTERCEPTORS: ");
    DotName interceptor = DotName.createSimple("javax.interceptor.Interceptor");
    final List<AnnotationInstance> annotations = index.getAnnotations(interceptor);
    for (AnnotationInstance annotationInstance : annotations) {
      final AnnotationTarget target = annotationInstance.target();
      switch (target.kind()) {
        case CLASS:
          final String name = annotationInstance.target().toString();
          INTERCEPTORS.add(name);
          System.out.println("  INTERCEPTOR: " + name);
          break;
      }
    }
  }

  public static void findDecorators(final Index index) {
    System.out.println("DECORATORS: ");
    DotName decorator = DotName.createSimple("javax.decorator.Decorator");
    final List<AnnotationInstance> annotations = index.getAnnotations(decorator);
    for (AnnotationInstance annotationInstance : annotations) {
      final AnnotationTarget target = annotationInstance.target();
      switch (target.kind()) {
        case CLASS:
          final String name = annotationInstance.target().toString();
          DECORATORS.add(name);
          System.out.println("  DECORATOR: " + name);
          break;
      }
    }
  }
}

