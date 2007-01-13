/*
 *
 * Copyright (c) 2004, 2005, 2006, 2007 void.fm
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name void.fm nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package etm.contrib.integration.spring.configuration;

import etm.contrib.aop.aopalliance.EtmMethodCallInterceptor;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 *
 * @version $Revision$
 * @author $Id$
 * @since 1.2.0
 */
public class MeasurementBeanDefinitionParser extends AbstractBeanDefinitionParser {


  protected AbstractBeanDefinition parseInternal(Element aElement, ParserContext aParserContext) {
    String monitorRef = aElement.getAttribute("monitor-ref");

    BeanDefinitionRegistry definitionRegistry = aParserContext.getRegistry();
    String[] names = definitionRegistry.getBeanDefinitionNames();
    boolean shouldInterceptorRegister = true;
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      BeanDefinition definition = definitionRegistry.getBeanDefinition(name);

      if (definition.getBeanClassName().equals("etm.contrib.aop.aopalliance.EtmMethodCallInterceptor")) {
        shouldInterceptorRegister = false;
      }
    }
    if (shouldInterceptorRegister) {
      BeanDefinitionBuilder interceptorBuilder = BeanDefinitionBuilder.rootBeanDefinition(EtmMethodCallInterceptor.class);
      if (monitorRef != null && monitorRef.length() > 0) {
        interceptorBuilder.addConstructorArgReference(monitorRef);
      } else {
        interceptorBuilder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
      }
      definitionRegistry.registerBeanDefinition("etmMethodCallInterceptor", interceptorBuilder.getBeanDefinition());
    }
    List list = DomUtils.getChildElementsByTagName(aElement, "beans");
    Element childElement = (Element) list.get(0);
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(BeanNameAutoProxyCreator.class);
    builder.addPropertyValue("interceptorNames", "etmMethodCallInterceptor");
    builder.addPropertyValue("beanNames", childElement.getTextContent());

    return builder.getBeanDefinition();
  }
}
