/*******************************************************************************
 * Copyright (c) 2008, 2013 Institute for Software, HSR Hochschule fuer Technik  
 * Rapperswil, University of applied sciences and others
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html  
 * 
 * Contributors: 
 *     Institute for Software - initial API and implementation
 *     Sergey Prigogin (Google)
 ******************************************************************************/
package org.eclipse.cdt.internal.core.dom.rewrite.commenthandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTArrayModifier;
import org.eclipse.cdt.core.dom.ast.IASTComment;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier.IASTEnumerator;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTPointerOperator;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorStatement;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTTypeId;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCompositeTypeSpecifier.ICPPASTBaseSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamespaceDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateParameter;
import org.eclipse.cdt.internal.core.dom.parser.ASTNode;

/**
 * This is the starting point of the entire comment handling  process. The creation of the 
 * NodeCommentMap is based on the IASTTranslationUnit. From this TranslationUnit the comments 
 * are extracted and skipped if they belong not to the same workspace. An ASTCommenterVisitor 
 * is initialized with this collection of comments. And the visit process can start. 
 * 
 * @see org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommenter
 * @see org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap
 *  
 * @author Guido Zgraggen IFS 
 */
public class ASTCommenter {
	
	private static final class PreprocessorRangeChecker extends ASTVisitor {
		int statementOffset;
		IASTFileLocation commentNodeLocation;
		boolean isPreStatementComment = true;
		
		private PreprocessorRangeChecker(int statementOffset, IASTFileLocation commentNodeLocation) {
			super(true);
			this.statementOffset = statementOffset;
			this.commentNodeLocation = commentNodeLocation;
		}

		private int checkOffsets(IASTNode node) {
			int offset = ((ASTNode) node).getOffset();
			int status = PROCESS_CONTINUE;
			
			if (isCommentOnSameLine(node) 
					|| offset > commentNodeLocation.getNodeOffset()
					&& offset < statementOffset) {
				isPreStatementComment = false;
				status = PROCESS_ABORT;
			} else if ((offset + ((ASTNode) node).getLength() < commentNodeLocation.getNodeOffset())) {
				status = PROCESS_SKIP;
			} else if (offset > statementOffset) {
				status = PROCESS_ABORT;
			}
			
			return status;
		}

		private boolean isCommentOnSameLine(IASTNode node) {
			if (node.getFileLocation() == null) {
				return false;
			} else {
				return commentNodeLocation.getStartingLineNumber() == node.getFileLocation()
						.getEndingLineNumber();
			}
		}

		@Override
		public int visit(ICPPASTBaseSpecifier baseSpecifier) {
			return checkOffsets(baseSpecifier);
		}
		
		@Override
		public int visit(ICPPASTNamespaceDefinition namespaceDefinition) {
			return checkOffsets(namespaceDefinition);
		}

		@Override
		public int visit(ICPPASTTemplateParameter templateParameter) {
			return checkOffsets(templateParameter);
		}

		@Override
		public int visit(IASTArrayModifier arrayModifier) {
			return checkOffsets(arrayModifier);
		}

		@Override
		public int visit(IASTDeclaration declaration) {
			return checkOffsets(declaration);
		}

		@Override
		public int visit(IASTDeclarator declarator) {
			return checkOffsets(declarator);
		}

		@Override
		public int visit(IASTDeclSpecifier declSpec) {
			return checkOffsets(declSpec);
		}

		@Override
		public int visit(IASTEnumerator enumerator) {
			return checkOffsets(enumerator);
		}

		@Override
		public int visit(IASTExpression expression) {
			return checkOffsets(expression);
		}

		@Override
		public int visit(IASTInitializer initializer) {
			return checkOffsets(initializer);
		}

		@Override
		public int visit(IASTName name) {
			return checkOffsets(name);
		}

		@Override
		public int visit(IASTParameterDeclaration parameterDeclaration) {
			return checkOffsets(parameterDeclaration);
		}

		@Override
		public int visit(IASTPointerOperator ptrOperator) {
			return checkOffsets(ptrOperator);
		}

		@Override
		public int visit(IASTStatement statement) {
			return checkOffsets(statement);
		}

		@Override
		public int visit(IASTTranslationUnit tu) {
			return checkOffsets(tu);
		}

		@Override
		public int visit(IASTTypeId typeId) {
			return checkOffsets(typeId);
		}
	}

	/**
	 * Creates a NodeCommentMap for the given AST. This is the only way to get a NodeCommentMap
	 * which contains all the comments mapped against nodes.
	 * 
	 * @param ast the AST
	 * @return NodeCommentMap
	 */
	public static NodeCommentMap getCommentedNodeMap(IASTTranslationUnit ast) {
		NodeCommentMap commentMap = new NodeCommentMap();
		if (ast == null) {
			return commentMap;
		}
		IASTComment[] commentsArray = ast.getComments();
		List<IASTComment> comments = new ArrayList<IASTComment>(commentsArray.length);
		for (IASTComment comment : commentsArray) {
			if (comment.isPartOfTranslationUnitFile()) {
				comments.add(comment);
			}
		}
		assignPreprocessorComments(commentMap, comments, ast);
		CommentHandler commentHandler = new CommentHandler(comments);
		ASTCommenterVisitor commenter = new ASTCommenterVisitor(commentHandler, commentMap);
		ast.accept(commenter);
		return commentMap;
	}

	private static boolean isCommentDirectlyBeforePreprocessorStatement(IASTComment comment,
			IASTPreprocessorStatement statement, IASTTranslationUnit tu) {
		if (tu == null || tu.getDeclarations().length == 0) {
			return true;
		}
		IASTFileLocation commentLocation = comment.getFileLocation();
		int preprocessorOffset = statement.getFileLocation().getNodeOffset();
		if (preprocessorOffset > commentLocation.getNodeOffset()) {
			PreprocessorRangeChecker visitor = new PreprocessorRangeChecker(preprocessorOffset, commentLocation);
			tu.accept(visitor);
			return visitor.isPreStatementComment;
		}
		return false;
	}

	public static boolean isInWorkspace(IASTNode node) {
		return node.isPartOfTranslationUnitFile();
	}
	
	/**
	 * Puts leading and trailing comments to {@code commentMap} and removes them from
	 * the {@code comments} list. 
	 */
	private static void assignPreprocessorComments(NodeCommentMap commentMap,
			List<IASTComment> comments, IASTTranslationUnit tu) {
		IASTPreprocessorStatement[] preprocessorStatementsArray = tu.getAllPreprocessorStatements();
		if (preprocessorStatementsArray == null) {
			return;
		}
		List<IASTPreprocessorStatement> preprocessorStatements = Arrays.asList(preprocessorStatementsArray);

		if (preprocessorStatements.isEmpty() || comments.isEmpty()) {
			return;
		}

		List<IASTComment> freestandingComments = new ArrayList<IASTComment>(comments.size());
		Iterator<IASTPreprocessorStatement> statementsIter = preprocessorStatements.iterator();
		Iterator<IASTComment> commentIter = comments.iterator();
		IASTPreprocessorStatement curStatement = getNextNodeInTu(statementsIter);
		IASTComment curComment = getNextNodeInTu(commentIter);
		while (curStatement != null && curComment != null) {
			int statementLineNr = curStatement.getFileLocation().getStartingLineNumber();
			int commentLineNr = curComment.getFileLocation().getStartingLineNumber();
			if (commentLineNr == statementLineNr) {
				commentMap.addTrailingCommentToNode(curStatement, curComment);
				curComment = getNextNodeInTu(commentIter);
			} else if (commentLineNr > statementLineNr) {
				curStatement = getNextNodeInTu(statementsIter);
			} else if (isCommentDirectlyBeforePreprocessorStatement(curComment, curStatement, tu)) {
				commentMap.addLeadingCommentToNode(curStatement, curComment);
				curComment = getNextNodeInTu(commentIter);
			} else {
				freestandingComments.add(curComment);
				curComment = getNextNodeInTu(commentIter);
			}
		}
		while (curComment != null) {
			freestandingComments.add(curComment);
			curComment = getNextNodeInTu(commentIter);
		}

		if (freestandingComments.size() != comments.size()) {
			comments.clear();
			comments.addAll(freestandingComments);
		}
	}

	private static <T extends IASTNode> T getNextNodeInTu(Iterator<T> iter) {
		while (iter.hasNext()) {
			T next = iter.next();
			if (next.isPartOfTranslationUnitFile())
				return next;
		}
		return null;
	}
}
