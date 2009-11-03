/*******************************************************************************
 * Copyright (c) 2009 Progress Software, Inc.
 * Copyright (c) 2004, 2007 IBM Corporation and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.fusesource.hawtjni.generator;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fusesource.hawtjni.generator.model.JNIClass;
import org.fusesource.hawtjni.generator.model.JNIMethod;
import org.fusesource.hawtjni.generator.model.JNIType;
import org.fusesource.hawtjni.generator.model.ReflectClass;
import org.fusesource.hawtjni.generator.model.ReflectType;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class LockGenerator extends CleanupClass {

    public LockGenerator() {
    }

    String getParams(JNIMethod method) {
        int n_args = method.getParameters().size();
        if (n_args == 0)
            return "";
        String name = method.getName();
        String params = "";
        int index = 0;
        while (true) {
            index = classSource.indexOf(name, index + 1);
            if (!Character.isWhitespace(classSource.charAt(index - 1)))
                continue;
            if (index == -1)
                return null;
            int parantesesStart = classSource.indexOf("(", index);
            if (classSource.substring(index + name.length(), parantesesStart).trim().length() == 0) {
                int parantesesEnd = classSource.indexOf(")", parantesesStart);
                params = classSource.substring(parantesesStart + 1, parantesesEnd);
                break;
            }
        }
        return params;
    }

    String getReturn(JNIMethod method) {
        JNIType returnType = method.getReturnType32();
        if (!returnType.isType("int"))
            return returnType.getTypeSignature3(false);
        String modifierStr = Modifier.toString(method.getModifiers());
        String name = method.getName();
        Pattern p = Pattern.compile(modifierStr + ".*" + name + ".*(.*)");
        Matcher m = p.matcher(classSource);
        if (m.find()) {
            String methodStr = classSource.substring(m.start(), m.end());
            int index = methodStr.indexOf("/*long*/");
            if (index != -1 && index < methodStr.indexOf(name)) {
                return new ReflectType(Integer.TYPE).getTypeSignature3(false) + " /*long*/";
            }
        }
        return new ReflectType(Integer.TYPE).getTypeSignature3(false);
    }

    public void generate(JNIClass clazz) {
        super.generate(clazz);
        generate(clazz.getDeclaredMethods());
    }

    public void generate(List<JNIMethod> methods) {
        sortMethods(methods);
        for (JNIMethod method : methods) {
            if ((method.getModifiers() & Modifier.NATIVE) == 0)
                continue;
            generate(method);
        }
    }

    public void generate(JNIMethod method) {
        int modifiers = method.getModifiers();
        boolean lock = (modifiers & Modifier.SYNCHRONIZED) != 0;
        String returnStr = getReturn(method);
        String paramsStr = getParams(method);
        if (lock) {
            String modifiersStr = Modifier.toString(modifiers & ~Modifier.SYNCHRONIZED);
            output(modifiersStr);
            if (modifiersStr.length() > 0)
                output(" ");
            output(returnStr);
            output(" _");
            output(method.getName());
            output("(");
            output(paramsStr);
            outputln(");");
        }
        String modifiersStr = Modifier.toString(modifiers & ~(Modifier.SYNCHRONIZED | (lock ? Modifier.NATIVE : 0)));
        output(modifiersStr);
        if (modifiersStr.length() > 0)
            output(" ");
        output(returnStr);
        output(" ");
        output(method.getName());
        output("(");
        output(paramsStr);
        output(")");
        if (lock) {
            outputln(" {");
            outputln("\tlock.lock();");
            outputln("\ttry {");
            output("\t\t");
            if (!method.getReturnType32().isType("void")) {
                output("return ");
            }
            output("_");
            output(method.getName());
            output("(");
            String[] paramNames = getArgNames(method);
            for (int i = 0; i < paramNames.length; i++) {
                if (i != 0)
                    output(", ");
                output(paramNames[i]);
            }
            outputln(");");
            outputln("\t} finally {");
            outputln("\t\tlock.unlock();");
            outputln("\t}");
            outputln("}");
        } else {
            outputln(";");
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java LockGenerator <OS className> <OS class source>");
            return;
        }
        try {
            LockGenerator gen = new LockGenerator();
            String clazzName = args[0];
            String classSource = args[1];
            Class<?> clazz = Class.forName(clazzName);
            gen.setClassSourcePath(classSource);
            gen.generate(new ReflectClass(clazz));
        } catch (Exception e) {
            System.out.println("Problem");
            e.printStackTrace(System.out);
        }
    }

}
