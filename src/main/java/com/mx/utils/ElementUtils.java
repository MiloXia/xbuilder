package com.mx.utils;

import javax.lang.model.element.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author milo
 */
public final class ElementUtils {

    public static boolean hasAllArgsConstructor(TypeElement classElement, Set<Modifier> modifiers) {
        if (!isClass(classElement)) {
            return false;
        }
        int fieldNum = getFields(classElement).size();
        if (modifiers == null) {
            modifiers = Collections.emptySet();
        }
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructorElement = (ExecutableElement) enclosed;
                if (constructorElement.getParameters().size() == fieldNum) {
                    // Found an all args constructor
                    return modifiers.isEmpty() || constructorElement.getModifiers().containsAll(modifiers);
                }
            }
        }
        return false;
    }

    public static boolean hasNoArgsConstructor(TypeElement classElement, Set<Modifier> modifiers) {
        if (!isClass(classElement)) {
            return false;
        }
        if (modifiers == null) {
            modifiers = Collections.emptySet();
        }
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructorElement = (ExecutableElement) enclosed;
                if (constructorElement.getParameters().size() == 0) {
                    // Found an empty constructor
                    return modifiers.isEmpty() || constructorElement.getModifiers().containsAll(modifiers);
                }
            }
        }
        return false;
    }

    public static List<VariableElement> getFields(Element classElement) {
        if (!isClass(classElement)) {
            return Collections.emptyList();
        }
        return classElement.getEnclosedElements().stream()
                .filter(elm -> elm.getKind() == ElementKind.FIELD)
                .map(elm -> (VariableElement) elm)
                .collect(Collectors.toList());
    }

    public static boolean isClass(Element element) {
        return element.getKind() == ElementKind.CLASS;
    }

    public static boolean isPublicClass(Element element) {
        return isClass(element) && element.getModifiers().contains(Modifier.PUBLIC);
    }

    public static boolean isAbstractClass(Element element) {
        return isClass(element) && element.getModifiers().contains(Modifier.ABSTRACT);
    }
}
