package net.ravendb.client.documents.operations.timeSeries;

import net.ravendb.client.documents.IDocumentStore;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.IOperation;
import net.ravendb.client.documents.session.loaders.ITimeSeriesIncludeBuilder;
import net.ravendb.client.documents.session.loaders.TimeSeriesIncludeBuilder;
import net.ravendb.client.http.HttpCache;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.NetISO8601Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;
import java.util.Date;
import java.util.function.Consumer;

public class GetTimeSeriesOperation implements IOperation<TimeSeriesRangeResult> {
    private final String _docId;
    private final String _name;
    private final int _start;
    private final int _pageSize;
    private final Date _from;
    private final Date _to;
    private final Consumer<ITimeSeriesIncludeBuilder> _includes;

    private final boolean _returnFullResults;

    public GetTimeSeriesOperation(String docId, String timeseries) {
        this(docId, timeseries, null, null, 0, Integer.MAX_VALUE);
    }

    public GetTimeSeriesOperation(String docId, String timeseries, Date from, Date to) {
        this(docId, timeseries, from, to, 0, Integer.MAX_VALUE);
    }

    public GetTimeSeriesOperation(String docId, String timeseries, Date from, Date to, int start) {
        this(docId, timeseries, from, to, start, Integer.MAX_VALUE);
    }

    public GetTimeSeriesOperation(String docId, String timeseries, Date from, Date to, int start, int pageSize) {
        this(docId, timeseries, from, to, start, pageSize, null);
    }

    public GetTimeSeriesOperation(String docId, String timeseries, Date from, Date to, int start, int pageSize, Consumer<ITimeSeriesIncludeBuilder> includes) {
        this(docId, timeseries, from, to, start, pageSize, includes, false);
    }

    public GetTimeSeriesOperation(String docId, String timeseries, Date from, Date to, int start, int pageSize, Consumer<ITimeSeriesIncludeBuilder> includes, boolean returnFullResults) {
        if (StringUtils.isEmpty(docId)) {
            throw new IllegalArgumentException("DocId cannot be null or empty");
        }
        if (StringUtils.isEmpty(timeseries)) {
            throw new IllegalArgumentException("Timeseries cannot be null or empty");
        }

        _docId = docId;
        _start = start;
        _pageSize = pageSize;
        _name = timeseries;
        _from = from;
        _to = to;
        _includes = includes;
        _returnFullResults = returnFullResults;
    }

    @Override
    public RavenCommand<TimeSeriesRangeResult> getCommand(IDocumentStore store, DocumentConventions conventions, HttpCache cache) {
        return new GetTimeSeriesCommand(_docId, _name, _from, _to, _start, _pageSize, _includes, _returnFullResults);
    }

    public static class GetTimeSeriesCommand extends RavenCommand<TimeSeriesRangeResult> {
        private final String _docId;
        private final String _name;
        private final int _start;
        private final int _pageSize;
        private final Date _from;
        private final Date _to;
        private final Consumer<ITimeSeriesIncludeBuilder> _includes;

        private final boolean _returnFullResults;

        public GetTimeSeriesCommand(String docId, String name, Date from,
                                    Date to, int start, int pageSize,
                                    Consumer<ITimeSeriesIncludeBuilder> includes, boolean returnFullResults) {
            super(TimeSeriesRangeResult.class);

            _docId = docId;
            _name = name;
            _start = start;
            _pageSize = pageSize;
            _from = from;
            _to = to;
            _includes = includes;
            _returnFullResults = returnFullResults;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            StringBuilder pathBuilder = new StringBuilder(node.getUrl());
            pathBuilder
                    .append("/databases/")
                    .append(node.getDatabase())
                    .append("/timeseries")
                    .append("?docId=")
                    .append(urlEncode(_docId));

            if (_start > 0) {
                pathBuilder
                        .append("&start=")
                        .append(_start);
            }

            if (_pageSize < Integer.MAX_VALUE) {
                pathBuilder
                        .append("&pageSize=")
                        .append(_pageSize);
            }

            pathBuilder
                    .append("&name=")
                    .append(urlEncode(_name));

            if (_from != null) {
                pathBuilder
                        .append("&from=")
                        .append(NetISO8601Utils.format(_from, true));
            }

            if (_to != null) {
                pathBuilder
                        .append("&to=")
                        .append(NetISO8601Utils.format(_to, true));
            }

            if (_includes != null) {
                addIncludesToRequest(pathBuilder, _includes);
            }

            if (_returnFullResults) {
                pathBuilder
                        .append("&full=")
                        .append(_returnFullResults);
            }

            String url = pathBuilder.toString();

            return new HttpGet(url);
        }

        public static void addIncludesToRequest(StringBuilder pathBuilder, Consumer<ITimeSeriesIncludeBuilder> includes) {
            TimeSeriesIncludeBuilder includeBuilder = new TimeSeriesIncludeBuilder(DocumentConventions.defaultConventions);
            includes.accept(includeBuilder);


            if (includeBuilder.includeTimeSeriesDocument) {
                pathBuilder
                        .append("&includeDocument=true");
            }

            if (includeBuilder.includeTimeSeriesTags) {
                pathBuilder
                        .append("&includeTags=true");
            }
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                return;
            }

            result = mapper.readValue(response, resultClass);
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }
    }
}
