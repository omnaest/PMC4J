package org.omnaest.library.pmc.rest.domain.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult
{
    @JsonProperty
    private ESearchResult esearchresult;

    public ESearchResult getEsearchresult()
    {
        return this.esearchresult;
    }

    public void setEsearchresult(ESearchResult esearchresult)
    {
        this.esearchresult = esearchresult;
    }

    @Override
    public String toString()
    {
        return "SearchResult [esearchresult=" + this.esearchresult + "]";
    }

}
