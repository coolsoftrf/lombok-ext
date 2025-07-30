/*
 * Copyright (C) 2009-2021 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.eclipse.handlers;

import static lombok.eclipse.handlers.EclipseHandlerUtil.getFieldType;
import static lombok.eclipse.handlers.EclipseHandlerUtil.injectMethod;
import static lombok.eclipse.handlers.EclipseHandlerUtil.methodExists;
import static lombok.eclipse.handlers.EclipseHandlerUtil.setGeneratedBy;
import static lombok.eclipse.handlers.EclipseHandlerUtil.toEclipseModifier;

import java.lang.reflect.Modifier;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

import lombok.AccessLevel;
import lombok.Lookup;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.LombokImmutableList;
import lombok.core.handlers.HandlerUtil.FieldAccess;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.spi.Provides;

/**
 * Handles the {@code Lookup} annotation for eclipse.
 */
@Provides public class HandleLookup extends EclipseAnnotationHandler<Lookup> {
	private static final String ATTR_FIELD_NAME = "field";
	
	public void handle(AnnotationValues<Lookup> annotation, Annotation ast, EclipseNode annotationNode) {
		Lookup ann = annotation.getInstance();
		if (!annotation.isExplicit(ATTR_FIELD_NAME)) {
			annotationNode.addError("'" + ATTR_FIELD_NAME + "' attribute is mandatory");
			return;
		}
		String fieldName = ann.field();
		generate(annotationNode.up(), fieldName, annotationNode);
	}
	
	public void generate(EclipseNode typeNode, String fieldName, EclipseNode errorNode) {
		if (!typeNode.isEnumType()) {
			errorNode.addError("@Lookup is only supported on an enum type.");
			return;
		}
		
		switch (methodExists("lookup", typeNode, 0)) {
		case NOT_EXISTS:
			EclipseNode field = null;
			LombokImmutableList<EclipseNode> children = typeNode.down();
			for (EclipseNode node : children) {
				if (node.getKind() == Kind.FIELD && node.getName().equals(fieldName)) {
					field = node;
				}
			}
			if (field == null) {
				typeNode.addError("'" + ATTR_FIELD_NAME + "' field not found");
			}
			
			MethodDeclaration lookup = create(typeNode, fieldName, getFieldType(field, FieldAccess.PREFER_FIELD), errorNode.get());
			injectMethod(typeNode, lookup);
			break;
		case EXISTS_BY_LOMBOK:
			break;
		default:
		case EXISTS_BY_USER:
			errorNode.addWarning("Not generating lookup(): A method with that name already exists");
		}
	}
	
	public static MethodDeclaration create(EclipseNode type, String fieldName, TypeReference fieldType, ASTNode source) {
		int pS = source.sourceStart, pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		Expression current = new NullLiteral(pS, pE);
		
		ReturnStatement returnStatement = new ReturnStatement(current, pS, pE);
		setGeneratedBy(returnStatement, source);
		
		MethodDeclaration method = new MethodDeclaration(((CompilationUnitDeclaration) type.top().get()).compilationResult);
		setGeneratedBy(method, source);
		method.modifiers = toEclipseModifier(AccessLevel.PUBLIC) | ClassFileConstants.AccStatic;
		TypeDeclaration typeDecl = (TypeDeclaration) type.get();
		method.returnType = EclipseHandlerUtil.namePlusTypeParamsToTypeReference(type, typeDecl.typeParameters, p);
		setGeneratedBy(method.returnType, source);
		method.arguments = new Argument[] {new Argument(fieldName.toCharArray(), 0, fieldType, Modifier.FINAL)};
		method.selector = "lookup".toCharArray();
		method.thrownExceptions = null;
		method.typeParameters = null;
		method.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		method.bodyStart = method.declarationSourceStart = method.sourceStart = source.sourceStart;
		method.bodyEnd = method.declarationSourceEnd = method.sourceEnd = source.sourceEnd;
		method.statements = new Statement[] {returnStatement};
		EclipseHandlerUtil.createRelevantNonNullAnnotation(type, method);
		return method;
	}
}
