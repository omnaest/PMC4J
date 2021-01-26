package org.omnaest.library.pmc.rest.domain.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Author
{
    @JsonProperty
    private String name;

    @JsonProperty
    private String authtype;

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getAuthtype()
    {
        return this.authtype;
    }

    public void setAuthtype(String authtype)
    {
        this.authtype = authtype;
    }

    @Override
    public String toString()
    {
        return "Author [name=" + this.name + ", authtype=" + this.authtype + "]";
    }

}
