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
package lombok.javac.handlers;

import static lombok.javac.Javac.CTC_BOT;
import static lombok.javac.handlers.JavacHandlerUtil.cloneType;
import static lombok.javac.handlers.JavacHandlerUtil.createRelevantNonNullAnnotation;
import static lombok.javac.handlers.JavacHandlerUtil.deleteAnnotationIfNeccessary;
import static lombok.javac.handlers.JavacHandlerUtil.injectMethod;
import static lombok.javac.handlers.JavacHandlerUtil.isClassOrEnum;
import static lombok.javac.handlers.JavacHandlerUtil.methodExists;
import static lombok.javac.handlers.JavacHandlerUtil.recursiveSetGeneratedBy;
import static lombok.javac.handlers.JavacHandlerUtil.removePrefixFromField;

import java.util.ArrayList;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import lombok.Lookup;
import lombok.ToString;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.LombokImmutableList;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.spi.Provides;

@Provides public class HandleLookup extends JavacAnnotationHandler<Lookup> {
	private static final String ATTR_FIELD_NAME = "field";
	private static final String ATTR_ARG_ORD = "constructorArgumentOrdinal";
	
	String fieldName;
	int fieldOrdinal;
	String defaultValue;
	
	@Override public void handle(AnnotationValues<Lookup> annotation, JCAnnotation ast, JavacNode annotationNode) {
		deleteAnnotationIfNeccessary(annotationNode, ToString.class);

		Lookup ann = annotation.getInstance();
		if (!annotation.isExplicit(ATTR_FIELD_NAME)) {
			annotationNode.addError("'" + ATTR_FIELD_NAME + "' attribute is mandatory");
			return;
		}
		if (!annotation.isExplicit(ATTR_ARG_ORD)) {
			annotationNode.addError("'" + ATTR_ARG_ORD + "' attribute is mandatory");
			return;
		}
		
		fieldName = ann.field();
		fieldOrdinal = ann.constructorArgumentOrdinal();
		defaultValue = ann.defaultValue();
		generate(annotationNode.up(), annotationNode);
	}
	
	public void generate(JavacNode typeNode, JavacNode source) {
		if (!isClassOrEnum(typeNode)) {
			source.addError("@Lookup is only supported on an enum type.");
			return;
		}
		
		switch (methodExists("lookup", typeNode, 0)) {
		case NOT_EXISTS:
			LombokImmutableList<JavacNode> children = typeNode.down();
			JavacNode fieldNode = null;
			for (JavacNode node : children) {
				if (node.getKind() == Kind.FIELD && node.getName().equals(fieldName)) {
					fieldNode = node;
				}
			}
			if (fieldNode == null) {
				typeNode.addError("'" + fieldName + "' field not found, required for @Lookup");
				return;
			}
			
			JCMethodDecl method = create(typeNode, fieldNode, source);
			injectMethod(typeNode, method);
			break;
		case EXISTS_BY_LOMBOK:
			break;
		default:
		case EXISTS_BY_USER:
			source.addWarning("Not generating lookup(): A method with that name already exists");
			break;
		}
	}
	
	private JCMethodDecl create(JavacNode typeNode, JavacNode fieldNode, JavacNode source) {
		Name eFieldName = removePrefixFromField(fieldNode);
		JCClassDecl enumNode = (JCClassDecl) typeNode.get();
		JavacTreeMaker maker = typeNode.getTreeMaker();
		JCExpression returnType = maker.Ident(enumNode.name);
		
		JCExpression fieldType = cloneType(maker, ((JCVariableDecl) fieldNode.get()).vartype, typeNode);
		JCVariableDecl pFieldName = maker.VarDef(maker.Modifiers(Flags.PARAMETER | Flags.FINAL), eFieldName, fieldType, null);
		List<JCVariableDecl> params = List.of(pFieldName);
		
		JCVariableDecl defaultNode = null;
		LombokImmutableList<JavacNode> children = typeNode.down();
		java.util.List<JCVariableDecl> consts = new ArrayList<JCVariableDecl>(children.size());
		for (JavacNode node : children) {
			JCTree childNode = node.get();
			if (childNode instanceof JCVariableDecl && ((JCVariableDecl) childNode).vartype.type.tsym.equals(enumNode.sym)) {
				if (node.getName().equals(defaultValue)) {
					defaultNode = (JCVariableDecl) node.get();
				}
				consts.add((JCVariableDecl) childNode);
			}
		}
		if (consts.isEmpty()) {
			typeNode.addWarning("No enum values detected, lookup() will always return default value");
		}
		
		ListBuffer<JCCase> cases = new ListBuffer<JCCase>();
		for (JCVariableDecl constNode : consts) {
			cases.add(maker.Case(((JCNewClass) constNode.init).args.get(fieldOrdinal), List.of((JCStatement) maker.Return(maker.Ident(constNode)))));
		}
		cases.add(maker.Case(null, List.of((JCStatement) maker.Return(defaultNode == null ? maker.Literal(CTC_BOT, null) : maker.Ident(defaultNode)))));
		
		JCStatement current = maker.Switch(maker.Ident(eFieldName), cases.toList());
		JCBlock body = maker.Block(0, List.of(current));
		
		JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
		JCMethodDecl methodDef = maker.MethodDef(mods, typeNode.toName("lookup"), returnType, List.<JCTypeParameter>nil(), params, List.<JCExpression>nil(), body, null);
		createRelevantNonNullAnnotation(typeNode, methodDef);
		return recursiveSetGeneratedBy(methodDef, source);
	}
}
