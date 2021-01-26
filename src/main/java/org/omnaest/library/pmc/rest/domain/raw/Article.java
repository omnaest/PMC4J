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
