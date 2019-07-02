/*
 This file is part of Jpsonic.

 Jpsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Jpsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Jpsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2019 (C) tesshu.com
 */
package com.tesshu.jpsonic.service.search;

import com.tesshu.jpsonic.service.search.IndexType.FieldNames;

import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.tesshu.jpsonic.service.search.IndexType.ALBUM;
import static com.tesshu.jpsonic.service.search.IndexType.ALBUM_ID3;
import static com.tesshu.jpsonic.service.search.IndexType.ARTIST;
import static com.tesshu.jpsonic.service.search.IndexType.ARTIST_ID3;
import static com.tesshu.jpsonic.service.search.IndexType.SONG;
import static org.springframework.util.ObjectUtils.isEmpty;

/**
 * Termination used by SearchService.
 * 
 * Since SearchService operates as a proxy for storage (DB) using lucene,
 * there are many redundant descriptions different from essential data processing.
 * This class is a transfer class for saving those redundant descriptions.
 * 
 * Methods and function such as unimportant data interchange and conversion are consolidated.
 * Processing with exceptions should not be described.
 */
@Component
public class SearchServiceTermination {

    /* Search by id only. */
    @Autowired
    private ArtistDao artistDao;

    /* Search by id only. */
    @Autowired
    private AlbumDao albumDao;

    /* Search by id only. Don't use MediaFileDao! */
    @Autowired
    private MediaFileService mediaFileService;
    
    public final Function<Long, Integer> round = (i) -> {
        // return
        // NumericUtils.floatToSortableInt(i);
        return i.intValue();
    };

    public final Function<Document, Integer> getId = d -> {
        return Integer.valueOf(d.get(FieldNames.ID));
    };

    public final BiConsumer<List<MediaFile>, Integer> addMediaFileIfAnyMatch = (dist, id) -> {
        if (!dist.stream().anyMatch(m -> id == m.getId())) {
            MediaFile mediaFile = mediaFileService.getMediaFile(id);
            if (!isEmpty(mediaFile)) {
                dist.add(mediaFile);
            }
        }
    };

    public final BiConsumer<List<Artist>, Integer> addArtistId3IfAnyMatch = (dist, id) -> {
        if (!dist.stream().anyMatch(a -> id == a.getId())) {
            Artist artist = artistDao.getArtist(id);
            if (!isEmpty(artist)) {
                dist.add(artist);
            }
        }
    };

    public final Function<Class<?>, @Nullable IndexType> getIndexType = (assignableClass) -> {
        IndexType indexType = null;
        if (assignableClass.isAssignableFrom(Album.class)) {
            indexType = IndexType.ALBUM_ID3;
        } else if (assignableClass.isAssignableFrom(Artist.class)) {
            indexType = IndexType.ARTIST_ID3;
        } else if (assignableClass.isAssignableFrom(MediaFile.class)) {
            indexType = IndexType.SONG;
        }
        return indexType;
    };

    public final Function<Class<?>, @Nullable String> getFieldName = (assignableClass) -> {
        String fieldName = null;
        if (assignableClass.isAssignableFrom(Album.class)) {
            fieldName = FieldNames.ALBUM;
        } else if (assignableClass.isAssignableFrom(Artist.class)) {
            fieldName = FieldNames.ARTIST;
        } else if (assignableClass.isAssignableFrom(MediaFile.class)) {
            fieldName = FieldNames.TITLE;
        }
        return fieldName;
    };

    public final BiConsumer<List<Album>, Integer> addAlbumId3IfAnyMatch = (dist, subjectId) -> {
        if (!dist.stream().anyMatch(a -> subjectId == a.getId())) {
            Album album = albumDao.getAlbum(subjectId);
            if (!isEmpty(album)) {
                dist.add(album);
            }
        }
    };

    private final Function<String, File> getRootDirectory = (version) -> {
        return new File(SettingsService.getJpsonicHome(), version);
    };

    public final BiFunction<String, IndexType, File> getDirectory = (version, indexType) -> {
        return new File(getRootDirectory.apply(version), indexType.toString().toLowerCase());
    };

    public final Term createPrimarykey(Album album) {
      return new Term(FieldNames.ID, Integer.toString(album.getId()));
    };

    public final Term createPrimarykey(Artist artist) {
      return new Term(FieldNames.ID, Integer.toString(artist.getId()));
    };

    public final Term createPrimarykey(MediaFile mediaFile) {
      return new Term(FieldNames.ID, Integer.toString(mediaFile.getId()));
    };

    public final Term createPrimarykey(String genre) {
      return new Term(FieldNames.GENRE_KEY, genre);
    };

    public final boolean addIgnoreNull(Collection<?> collection, Object object) {
        return CollectionUtils.addIgnoreNull(collection, object);
    }

    public final boolean addIgnoreNull(Collection<?> collection, IndexType indexType, int subjectId) {
        if (indexType == ALBUM) {
            return addIgnoreNull(collection, mediaFileService.getMediaFile(subjectId));
        } else if (indexType == ALBUM_ID3) {
            return addIgnoreNull(collection, albumDao.getAlbum(subjectId));
        }
        return false;
    }

    public final <T> void addIgnoreNull(ParamSearchResult<T> dist, IndexType indexType, int subjectId, Class<T> subjectClass) {
        if (indexType == SONG) {
            MediaFile mediaFile = mediaFileService.getMediaFile(subjectId);
            addIgnoreNull(dist.getItems(), subjectClass.cast(mediaFile));
        } else if (indexType == ARTIST_ID3) {
            Artist artist = artistDao.getArtist(subjectId);
            addIgnoreNull(dist.getItems(), subjectClass.cast(artist));
        } else if (indexType == ALBUM_ID3) {
            Album album = albumDao.getAlbum(subjectId);
            addIgnoreNull(dist.getItems(), subjectClass.cast(album));
        }
    }

    public final void addIfAnyMatch(SearchResult dist, IndexType subjectIndexType, Document subject) {
        int documentId = getId.apply(subject);
        if (subjectIndexType == ARTIST | subjectIndexType == ALBUM | subjectIndexType == SONG) {
            addMediaFileIfAnyMatch.accept(dist.getMediaFiles(), documentId);
        } else if (subjectIndexType == ARTIST_ID3) {
            addArtistId3IfAnyMatch.accept(dist.getArtists(), documentId);
        } else if (subjectIndexType == ALBUM_ID3) {
            addAlbumId3IfAnyMatch.accept(dist.getAlbums(), documentId);
        }
    }

}