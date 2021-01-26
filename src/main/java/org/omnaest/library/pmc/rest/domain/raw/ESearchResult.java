package org.omnaest.library.pmc.rest.domain.raw;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ESearchResult
{
    @JsonProperty
    private String       count;
    @JsonProperty
    private String       retmax;
    @JsonProperty
    private String       retstart;
    @JsonProperty
    private List<String> idlist;

    public String getCount()
    {
        return this.count;
    }

    public void setCount(String count)
    {
        this.count = count;
    }

    public String getRetmax()
    {
        return this.retmax;
    }

    public void setRetmax(String retmax)
    {
        this.retmax = retmax;
    }

    public String getRetstart()
    {
        return this.retstart;
    }

    public void setRetstart(String retstart)
    {
        this.retstart = retstart;
    }

    public List<String> getIdlist()
    {
        return this.idlist;
    }

    public void setIdlist(List<String> idlist)
    {
        this.idlist = idlist;
    }

    @Override
    public String toString()
    {
        return "ESearchResult [count=" + this.count + ", retmax=" + this.retmax + ", retstart=" + this.retstart + ", idlist=" + this.idlist + "]";
    }

}
