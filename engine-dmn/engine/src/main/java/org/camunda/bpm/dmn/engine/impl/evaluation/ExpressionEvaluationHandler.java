/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.dmn.engine.impl.evaluation;

import static org.camunda.commons.utils.EnsureUtil.ensureNotNull;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.camunda.bpm.application.ProcessApplicationUnavailableException;
import org.camunda.bpm.dmn.engine.impl.CachedCompiledScriptSupport;
import org.camunda.bpm.dmn.engine.impl.CachedExpressionSupport;
import org.camunda.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.impl.DmnEngineLogger;
import org.camunda.bpm.dmn.engine.impl.DmnExpressionImpl;
import org.camunda.bpm.dmn.engine.impl.el.VariableContextScriptBindings;
import org.camunda.bpm.dmn.engine.impl.spi.el.DmnScriptEngineResolver;
import org.camunda.bpm.dmn.engine.impl.spi.el.ElExpression;
import org.camunda.bpm.dmn.engine.impl.spi.el.ElProvider;
import org.camunda.bpm.dmn.feel.impl.FeelEngine;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.variable.context.VariableContext;
import org.camunda.commons.utils.IoUtil;
import org.camunda.commons.utils.StringUtil;

import java.io.InputStream;

public class ExpressionEvaluationHandler {

    protected static final DmnEngineLogger LOG = DmnEngineLogger.ENGINE_LOGGER;

    protected final DmnScriptEngineResolver scriptEngineResolver;
    protected final ElProvider elProvider;
    protected final FeelEngine feelEngine;

    private final String JS_GLOBAL_FILENAME = "dmnGlobal.js";
    private final String JS_APP_FILENAME = "dmn.js";

    public ExpressionEvaluationHandler(DefaultDmnEngineConfiguration configuration) {
        this.scriptEngineResolver = configuration.getScriptEngineResolver();
        this.elProvider = configuration.getElProvider();
        this.feelEngine = configuration.getFeelEngine();
    }

    public Object evaluateExpression(String expressionLanguage, DmnExpressionImpl expression, VariableContext variableContext) {
        String expressionText = getExpressionTextForLanguage(expression, expressionLanguage);
        if (expressionText != null) {

            if (isFeelExpressionLanguage(expressionLanguage)) {
                return evaluateFeelSimpleExpression(expressionText, variableContext);

            } else if (isElExpression(expressionLanguage)) {
                return evaluateElExpression(expressionLanguage, expressionText, variableContext, expression);

            } else {
                return evaluateScriptExpression(expressionLanguage, variableContext, expressionText, expression);
            }
        } else {
            return null;
        }
    }

    private String augmentExpressionText(String expressionText) {
        //First, we'll pull the JavaScript from the process application using the application's classloader.
        //  NOTE: If this is not called from within the context of an application, we won't be able to
        //    load any application-specific JavaScript.
        //  NOTE: This has been confirmed to work in Tomcat if the file is placed in the application's
        //    "classes" directory.
        try {
            InputStream dmnJSPrefix = Context.getCurrentProcessApplication().getProcessApplication()
                    .getProcessApplicationClassloader().getResourceAsStream(JS_APP_FILENAME);
            String dmnJSPrefixString = "";
            if(dmnJSPrefix != null)
                dmnJSPrefixString = IoUtil.inputStreamAsString(dmnJSPrefix);
            expressionText = dmnJSPrefixString + expressionText;
        }
        catch(ProcessApplicationUnavailableException pauEx){
            //Do nothing; the process application is unavailable, so we can't load the JavaScript.
        }
        catch(NullPointerException npEx) {
            //Do nothing; this call was made from outside of the context of an application, so we can't
            //  load any process application-specific JavaScript.
        }

        //Second, we'll pull the JavaScript from the global file using this class' classloader.
        //  NOTE: This has been confirmed to work in Tomcat if the file is placed in the Tomcat "lib"
        //    folder.
        InputStream dmnJSGlobalPrefix = ExpressionEvaluationHandler.class
                .getClassLoader().getResourceAsStream(JS_GLOBAL_FILENAME);
        String dmnJSGlobalPrefixString = "";
        if(dmnJSGlobalPrefix != null)
            dmnJSGlobalPrefixString = IoUtil.inputStreamAsString(dmnJSGlobalPrefix);
        expressionText = dmnJSGlobalPrefixString + expressionText;

        //Return the resulting expressionText for evaluation.
        return expressionText;
    }

    protected Object evaluateScriptExpression(String expressionLanguage, VariableContext variableContext, String expressionText, CachedCompiledScriptSupport cachedCompiledScriptSupport) {
        ScriptEngine scriptEngine = getScriptEngineForName(expressionLanguage);
        // wrap script engine bindings + variable context and pass enhanced
        // bindings to the script engine.
        Bindings bindings = VariableContextScriptBindings.wrap(scriptEngine.createBindings(), variableContext);
        bindings.put("variableContext", variableContext);

        //Augment the expression text with JavaScript additions.
        expressionText = augmentExpressionText(expressionText);

        try {
            if (scriptEngine instanceof Compilable) {

                CompiledScript compiledScript = cachedCompiledScriptSupport.getCachedCompiledScript();
                if (compiledScript == null) {
                    synchronized (cachedCompiledScriptSupport) {
                        compiledScript = cachedCompiledScriptSupport.getCachedCompiledScript();

                        if(compiledScript == null) {
                            Compilable compilableScriptEngine = (Compilable) scriptEngine;
                            compiledScript = compilableScriptEngine.compile(expressionText);

                            cachedCompiledScriptSupport.cacheCompiledScript(compiledScript);
                        }
                    }
                }

                return compiledScript.eval(bindings);
            }
            else {
                return scriptEngine.eval(expressionText, bindings);
            }
        }
        catch (ScriptException e) {
            throw LOG.unableToEvaluateExpression(expressionText, scriptEngine.getFactory().getLanguageName(), e);
        }
    }

    protected Object evaluateElExpression(String expressionLanguage, String expressionText, VariableContext variableContext, CachedExpressionSupport cachedExpressionSupport) {
        try {
            ElExpression elExpression = cachedExpressionSupport.getCachedExpression();

            if (elExpression == null) {
                synchronized (cachedExpressionSupport) {
                    elExpression = cachedExpressionSupport.getCachedExpression();
                    if(elExpression == null) {
                        elExpression = elProvider.createExpression(expressionText);
                        cachedExpressionSupport.setCachedExpression(elExpression);
                    }
                }
            }

            return elExpression.getValue(variableContext);
        }
        // yes, we catch all exceptions
        catch(Exception e) {
            throw LOG.unableToEvaluateExpression(expressionText, expressionLanguage, e);
        }
    }

    protected Object evaluateFeelSimpleExpression(String expressionText, VariableContext variableContext) {
        return feelEngine.evaluateSimpleExpression(expressionText, variableContext);
    }

    // helper ///////////////////////////////////////////////////////////////////

    protected String getExpressionTextForLanguage(DmnExpressionImpl expression, String expressionLanguage) {
        String expressionText = expression.getExpression();
        if (expressionText != null) {
            if (isJuelExpression(expressionLanguage) && !StringUtil.isExpression(expressionText)) {
                return "${" + expressionText + "}";
            } else {
                return expressionText;
            }
        } else {
            return null;
        }
    }

    private boolean isJuelExpression(String expressionLanguage) {
        return DefaultDmnEngineConfiguration.JUEL_EXPRESSION_LANGUAGE.equalsIgnoreCase(expressionLanguage);
    }

    protected ScriptEngine getScriptEngineForName(String expressionLanguage) {
        ensureNotNull("expressionLanguage", expressionLanguage);
        ScriptEngine scriptEngine = scriptEngineResolver.getScriptEngineForLanguage(expressionLanguage);
        if (scriptEngine != null) {
            return scriptEngine;

        } else {
            throw LOG.noScriptEngineFoundForLanguage(expressionLanguage);
        }
    }

    protected boolean isElExpression(String expressionLanguage) {
        return isJuelExpression(expressionLanguage);
    }

    public boolean isFeelExpressionLanguage(String expressionLanguage) {
        ensureNotNull("expressionLanguage", expressionLanguage);
        return expressionLanguage.equals(DefaultDmnEngineConfiguration.FEEL_EXPRESSION_LANGUAGE) ||
                expressionLanguage.toLowerCase().equals(DefaultDmnEngineConfiguration.FEEL_EXPRESSION_LANGUAGE_ALTERNATIVE) ||
                expressionLanguage.equals(DefaultDmnEngineConfiguration.FEEL_EXPRESSION_LANGUAGE_DMN12);
    }

}
