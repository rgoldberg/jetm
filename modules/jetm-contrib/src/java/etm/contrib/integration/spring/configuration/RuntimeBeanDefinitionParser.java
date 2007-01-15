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

import etm.contrib.aggregation.log.CommonsLoggingAggregator;
import etm.contrib.aggregation.log.Jdk14LogAggregator;
import etm.contrib.aggregation.log.Log4jAggregator;
import etm.contrib.aggregation.persistence.FileSystemPersistenceBackend;
import etm.contrib.aggregation.persistence.PersistentFlatAggregator;
import etm.contrib.aggregation.persistence.PersistentNestedAggregator;
import etm.core.aggregation.BufferedThresholdAggregator;
import etm.core.aggregation.BufferedTimedAggregator;
import etm.core.aggregation.FlatAggregator;
import etm.core.aggregation.NestedAggregator;
import etm.core.timer.DefaultTimer;
import etm.core.timer.Java15NanoTimer;
import etm.core.timer.SunHighResTimer;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BeanDefinitionParser that parses a JETM runtime element.
 *
 * @author $Id$
 * @version $Revision$
 * @since 1.2.0
 */
public class RuntimeBeanDefinitionParser extends JetmBeanDefinitionParser {

  protected AbstractBeanDefinition parseInternal(Element aElement, ParserContext aParserContext) {
    String type = aElement.getAttribute("type");
    String timer = aElement.getAttribute("timer");

    Element features = DomUtils.getChildElementByTagName(aElement, "features");
    Element aggregatorChain = DomUtils.getChildElementByTagName(aElement, "aggregator-chain");
    Element extension = DomUtils.getChildElementByTagName(aElement, "extension");

    if (type == null || type.length() == 0) {
      type = "nested";
    }

    BeanDefinitionBuilder builder;
    if ("nested".equals(type)) {
      builder = BeanDefinitionBuilder.rootBeanDefinition(etm.core.monitor.NestedMonitor.class);
    } else if ("flat".equals(type)) {
      builder = BeanDefinitionBuilder.rootBeanDefinition(etm.core.monitor.FlatMonitor.class);
    } else {
      try {
        builder = BeanDefinitionBuilder.rootBeanDefinition(Class.forName(type));
      } catch (ClassNotFoundException e) {
        throw new FatalBeanException("Unable to locate monitor class " + type, e);
      }
    }
    if (timer != null && timer.length() > 0) {
      addTimerDefinition(timer, builder);
    }

    if (features != null) {
      buildChainFromFeatures(builder, features, type);
    } else if (aggregatorChain != null) {
      buildChainFromChain(builder, aggregatorChain, type);
    }

    if (extension != null) {
      addExtensions(builder, extension);
    }

    builder.setInitMethodName("start");
    builder.setDestroyMethodName("stop");
    return builder.getBeanDefinition();

  }

  private void addExtensions(BeanDefinitionBuilder aBuilder, Element aExtension) {
    List pluginConfigs = DomUtils.getChildElementsByTagName(aExtension, "plugin");

    if (pluginConfigs.size() > 0) {
      List plugins = new ArrayList();
      for (int i = 0; i < pluginConfigs.size(); i++) {

        Element element = (Element) pluginConfigs.get(i);
        String clazz = element.getAttribute("class");

        BeanDefinitionBuilder builder;
        try {

          builder = BeanDefinitionBuilder.rootBeanDefinition(Class.forName(clazz));
        } catch (ClassNotFoundException e) {
          throw new FatalBeanException("Unable to locate plugin class " + clazz, e);
        }

        List properties = DomUtils.getChildElementsByTagName(element, "property");
        if (properties.size() > 0) {
          for (int j = 0; j < properties.size(); j++) {
            Element aProperty = (Element) properties.get(j);
            builder.addPropertyValue(aProperty.getAttribute("name"), DomUtils.getTextValue(aProperty));
          }
        }
        plugins.add(builder.getBeanDefinition());

      }
      ManagedList list = new ManagedList(plugins.size());
      list.addAll(plugins);
      aBuilder.addPropertyValue("plugins", list);
    }
  }

  private void buildChainFromChain(BeanDefinitionBuilder aBuilder, Element aAggregatorChain, String aType) {
    throw new UnsupportedOperationException("Aggregator chain not supported yet.");
  }

  private void buildChainFromFeatures(BeanDefinitionBuilder runtimeBuilder, Element aElement, String monitorType) {
    Element thresholdBufferElement = DomUtils.getChildElementByTagName(aElement, "threshold-buffer");
    Element intervalBuffer = DomUtils.getChildElementByTagName(aElement, "interval-buffer");

    Element rawDataLog = DomUtils.getChildElementByTagName(aElement, "raw-data-log");
    Element persistence = DomUtils.getChildElementByTagName(aElement, "persistence");

    BeanDefinitionBuilder bufferBuilder;
    BeanDefinitionBuilder rawDataBuilder = null;
    BeanDefinitionBuilder aggregationRootBuilder;

    if (persistence != null) {
      if ("flat".equals(monitorType)) {
        aggregationRootBuilder = BeanDefinitionBuilder.rootBeanDefinition(PersistentFlatAggregator.class);
      } else {
        // fallback to nested
        aggregationRootBuilder = BeanDefinitionBuilder.rootBeanDefinition(PersistentNestedAggregator.class);
      }

      Element fileBackend = DomUtils.getChildElementByTagName(persistence, "file-backend");
      Element genericBackend = DomUtils.getChildElementByTagName(persistence, "custom-backend");

      BeanDefinitionBuilder backendBuilder;

      if (fileBackend != null) {
        backendBuilder = BeanDefinitionBuilder.rootBeanDefinition(FileSystemPersistenceBackend.class);


        String file = fileBackend.getAttribute("filename");
        String path = fileBackend.getAttribute("path");
        if (file != null && file.length() > 0) {
          backendBuilder.addPropertyValue("filename", file);
        }
        if (path != null && path.length() > 0) {
          backendBuilder.addPropertyValue("path", path);
        }

      } else if (genericBackend != null) {
        String className = genericBackend.getAttribute("class");
        try {
          backendBuilder = BeanDefinitionBuilder.rootBeanDefinition(Class.forName(className));
        } catch (ClassNotFoundException e) {
          throw new FatalBeanException("Unable to locate persistence backend class " + className, e);
        }
        List properties = DomUtils.getChildElementsByTagName(genericBackend, "property");
        for (Iterator iterator = properties.iterator(); iterator.hasNext();) {
          Element element = (Element) iterator.next();
          String key = element.getAttribute("name");
          String value = DomUtils.getTextValue(element);
          backendBuilder.addPropertyValue(key, value);
        }
      } else {
        backendBuilder = BeanDefinitionBuilder.rootBeanDefinition(FileSystemPersistenceBackend.class);
      }

      aggregationRootBuilder.addPropertyValue("persistenceBackend", backendBuilder.getBeanDefinition());
    } else {
      if ("flat".equals(monitorType)) {
        aggregationRootBuilder = BeanDefinitionBuilder.rootBeanDefinition(FlatAggregator.class);
      } else {
        // fallback to nested
        aggregationRootBuilder = BeanDefinitionBuilder.rootBeanDefinition(NestedAggregator.class);
      }
    }

    if (rawDataLog != null) {
      String logType = rawDataLog.getAttribute("type");
      String logCategory = rawDataLog.getAttribute("category");
      String logFormaterClass = rawDataLog.getAttribute("formatter-class");


      if ("log4j".equals(logType)) {
        rawDataBuilder = BeanDefinitionBuilder.rootBeanDefinition(Log4jAggregator.class);
      } else if ("commons".equals(logType)) {
        rawDataBuilder = BeanDefinitionBuilder.rootBeanDefinition(CommonsLoggingAggregator.class);
      } else if ("jdk14".equals(logType)) {
        rawDataBuilder = BeanDefinitionBuilder.rootBeanDefinition(Jdk14LogAggregator.class);
      } else {
        throw new BeanDefinitionStoreException("Raw logging type '" + logType + "' not supported");
      }

      if (logCategory != null && logCategory.length() > 0) {
        rawDataBuilder.addPropertyValue("logName", logCategory);
      }

      if (logFormaterClass != null && logFormaterClass.length() > 0) {
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setBeanClassName(logFormaterClass);
        rawDataBuilder.addPropertyValue("formatter", definition);
      }
    }


    if (thresholdBufferElement != null) {
      bufferBuilder = BeanDefinitionBuilder.rootBeanDefinition(BufferedThresholdAggregator.class);
      String threshold = thresholdBufferElement.getAttribute("threshold");

      if (threshold != null && threshold.length() > 0) {
        bufferBuilder.addPropertyValue("threshold", threshold);
      }

    } else if (intervalBuffer != null) {
      bufferBuilder = BeanDefinitionBuilder.rootBeanDefinition(BufferedTimedAggregator.class);
      String interval = intervalBuffer.getAttribute("threshold");

      if (interval != null && interval.length() > 0) {
        bufferBuilder.addPropertyValue("interval", interval);
      }
    } else {
      bufferBuilder = BeanDefinitionBuilder.rootBeanDefinition(BufferedThresholdAggregator.class);
    }

    // now build up chain
    BeanDefinitionBuilder chainBuilder = aggregationRootBuilder;

    if (rawDataBuilder != null) {
      rawDataBuilder.addConstructorArg(chainBuilder.getBeanDefinition());
      chainBuilder = rawDataBuilder;
    }

    bufferBuilder.addConstructorArg(chainBuilder.getBeanDefinition());
    chainBuilder = bufferBuilder;

    runtimeBuilder.addConstructorArg(chainBuilder.getBeanDefinition());

  }

  private void addTimerDefinition(String aTimer, BeanDefinitionBuilder builder) {
    if ("jdk15".equals(aTimer)) {
      builder.addConstructorArg(new Java15NanoTimer());
    } else if ("sun".equals(aTimer)) {
      builder.addConstructorArg(new SunHighResTimer());
    } else if ("default".equals(aTimer)) {
      builder.addConstructorArg(new DefaultTimer());
    } else {
      RootBeanDefinition timerBeanDefinition = new RootBeanDefinition();
      timerBeanDefinition.setBeanClassName(aTimer);
      builder.addConstructorArg(timerBeanDefinition);
    }
  }
}