/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.omnaest.library.pmc.rest.domain.raw;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Article
{
    @JsonProperty
    @JsonIgnoreProperties(ignoreUnknown = true, value = "uids")
    private Map<String, ArticleResult> result = new LinkedHashMap<>();

    public Map<String, ArticleResult> getResult()
    {
        return this.result;
    }

    public void setResult(Map<String, ArticleResult> result)
    {
        this.result = result;
    }

    @Override
    public String toString()
    {
        return "Article [result=" + this.result + "]";
    }

}
