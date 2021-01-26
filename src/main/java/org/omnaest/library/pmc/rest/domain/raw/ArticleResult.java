package org.omnaest.library.pmc.rest.domain.raw;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleResult
{
    @JsonProperty
    private String uid;

    @JsonProperty
    private String pubdate;

    @JsonProperty
    private String epubdate;

    @JsonProperty
    private String title;

    @JsonProperty
    private String volume;

    @JsonProperty
    private String issue;
    @JsonProperty
    private String source;

    @JsonProperty
    private List<Author> authors;

    @JsonProperty
    private List<ArticleId> articleids;

    public static class ArticleId
    {
        @JsonProperty
        private String idtype;

        @JsonProperty
        private String value;

        /**
         * pmid,doi,pmcid
         */
        public String getIdtype()
        {
            return this.idtype;
        }

        @JsonIgnore
        public boolean isPMC()
        {
            return StringUtils.equalsIgnoreCase("pmcid", this.idtype);
        }

        @JsonIgnore
        public boolean isDOI()
        {
            return StringUtils.equalsIgnoreCase("doi", this.idtype);
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "ArticleId [idtype=" + this.idtype + ", value=" + this.value + "]";
        }

        public void setIdtype(String idtype)
        {
            this.idtype = idtype;
        }

        public void setValue(String value)
        {
            this.value = value;
        }

    }

    public List<Author> getAuthors()
    {
        return this.authors;
    }

    public void setAuthors(List<Author> authors)
    {
        this.authors = authors;
    }

    public String getUid()
    {
        return this.uid;
    }

    public void setUid(String uid)
    {
        this.uid = uid;
    }

    public String getPubdate()
    {
        return this.pubdate;
    }

    public void setPubdate(String pubdate)
    {
        this.pubdate = pubdate;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getEpubdate()
    {
        return this.epubdate;
    }

    public void setEpubdate(String epubdate)
    {
        this.epubdate = epubdate;
    }

    public String getVolume()
    {
        return this.volume;
    }

    public void setVolume(String volume)
    {
        this.volume = volume;
    }

    public String getIssue()
    {
        return this.issue;
    }

    public void setIssue(String issue)
    {
        this.issue = issue;
    }

    public String getSource()
    {
        return this.source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public List<ArticleId> getArticleids()
    {
        return this.articleids;
    }

    public void setArticleids(List<ArticleId> articleids)
    {
        this.articleids = articleids;
    }

    @Override
    public String toString()
    {
        return "ArticleResult [uid=" + this.uid + ", pubdate=" + this.pubdate + ", epubdate=" + this.epubdate + ", title=" + this.title + ", volume="
                + this.volume + ", issue=" + this.issue + ", source=" + this.source + ", authors=" + this.authors + ", articleids=" + this.articleids + "]";
    }

}
