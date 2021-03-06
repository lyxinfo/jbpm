package org.jbpm.process.builder.dialect.mvel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.mvel.MVELCompilationUnit;
import org.drools.compiler.DescrBuildError;
import org.drools.compiler.Dialect;
import org.drools.compiler.ReturnValueDescr;
import org.drools.rule.MVELDialectRuntimeData;
import org.drools.rule.builder.PackageBuildContext;
import org.drools.rule.builder.dialect.mvel.MVELDialect;
import org.drools.spi.KnowledgeHelper;
import org.drools.spi.ProcessContext;
import org.jbpm.process.builder.ReturnValueEvaluatorBuilder;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.impl.MVELReturnValueEvaluator;
import org.jbpm.process.instance.impl.ReturnValueConstraintEvaluator;

public class MVELReturnValueEvaluatorBuilder
    implements
    ReturnValueEvaluatorBuilder {

    public MVELReturnValueEvaluatorBuilder() {

    }

    public void build(final PackageBuildContext context,
                      final ReturnValueConstraintEvaluator constraintNode,
                      final ReturnValueDescr descr,
                      final ContextResolver contextResolver) {

        String text = descr.getText();

        try {
            MVELDialect dialect = (MVELDialect) context.getDialect( "mvel" );

            Map<String, Class<?>> variables = new HashMap<String,Class<?>>();
            variables.put("context", ProcessContext.class);
            variables.put("kcontext", org.drools.runtime.process.ProcessContext.class);
            variables.put("drools", KnowledgeHelper.class);
            Dialect.AnalysisResult analysis = dialect.analyzeBlock( context,
                                                                    descr,
                                                                    dialect.getInterceptors(),
                                                                    text,
                                                                    new Map[]{variables, context.getPackageBuilder().getGlobals()},
                                                                    null );         

            List<String> variableNames = analysis.getNotBoundedIdentifiers();
            if (contextResolver != null) {
	            for (String variableName: variableNames) {
	            	VariableScope variableScope = (VariableScope) contextResolver.resolveContext(VariableScope.VARIABLE_SCOPE, variableName);
	            	if (variableScope == null) {
	            		context.getErrors().add(
	        				new DescrBuildError(
	    						context.getParentDescr(),
	                            descr,
	                            null,
	                            "Could not find variable '" + variableName + "' for constraint '" + descr.getText() + "'" ) );            		
	            	} else {
	            		variables.put(variableName,
            				context.getDialect().getTypeResolver().resolveType(
        						variableScope.findVariable(variableName).getType().getStringType()));
	            	}
	            }
            }
            
            MVELCompilationUnit unit = dialect.getMVELCompilationUnit( text,
                                                                       analysis,
                                                                       null,
                                                                       null,
                                                                       variables,
                                                                       context );  

            MVELReturnValueEvaluator expr = new MVELReturnValueEvaluator( unit,
                                                                          dialect.getId() );
            expr.setVariableNames(variableNames);

            constraintNode.setEvaluator( expr );
            
            MVELDialectRuntimeData data = (MVELDialectRuntimeData) context.getPkg().getDialectRuntimeRegistry().getDialectData( dialect.getId() );
            data.addCompileable( constraintNode,
                                  expr );
            
            expr.compile( context.getPackageBuilder().getRootClassLoader() );
        } catch ( final Exception e ) {
            context.getErrors().add( new DescrBuildError( context.getParentDescr(),
                                                          descr,
                                                          null,
                                                          "Unable to build expression for 'constraint' " + descr.getText() + "': " + e ) );
        }
    }

    /**
     * Allows newlines to demarcate expressions, as per MVEL command line.
     * If expression spans multiple lines (ie inside an unbalanced bracket) then
     * it is left alone.
     * Uses character based iteration which is at least an order of magnitude faster then a single
     * simple regex.
     */
    public static String delimitExpressions(String s) {

        StringBuilder result = new StringBuilder();
        char[] cs = s.toCharArray();
        int brace = 0;
        int sqre = 0;
        int crly = 0;
        char lastNonWhite = ';';
        for ( int i = 0; i < cs.length; i++ ) {
            char c = cs[i];
            switch ( c ) {
                case '(' :
                    brace++;
                    break;
                case '{' :
                    crly++;
                    break;
                case '[' :
                    sqre++;
                    break;
                case ')' :
                    brace--;
                    break;
                case '}' :
                    crly--;
                    break;
                case ']' :
                    sqre--;
                    break;
                default :
                    break;
            }
            if ( (brace == 0 && sqre == 0 && crly == 0) && (c == '\n' || c == '\r') ) {
                if ( lastNonWhite != ';' ) {
                    result.append( ';' );
                    lastNonWhite = ';';
                }
            } else if ( !Character.isWhitespace( c ) ) {
                lastNonWhite = c;
            }
            result.append( c );

        }
        return result.toString();
    }

}
