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
package org.airsonic.player.service.upnp.processor;

import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Genre;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.upnp.UpnpProcessDispatcher;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.SortCriterion;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.springframework.util.ObjectUtils.isEmpty;

@Service
public class AlbumByGenreUpnpProcessor extends UpnpContentProcessor <MediaFile, MediaFile> {

    private final SearchService searchService;

    private final MediaFileService mediaFileService;

    private final MediaFileDao mediaFileDao;

    public AlbumByGenreUpnpProcessor(UpnpProcessDispatcher dispatcher, SettingsService settingsService, SearchService searchService, JWTSecurityService jwtSecurityService,
            MediaFileService mediaFileService, MediaFileDao mediaFileDao) {
        super(dispatcher, settingsService, searchService, jwtSecurityService);
        this.searchService = searchService;
        this.mediaFileDao = mediaFileDao;
        this.mediaFileService = mediaFileService;
        setRootId(UpnpProcessDispatcher.CONTAINER_ID_ALBUM_BY_GENRE_PREFIX);
    }

    @PostConstruct
    public void initTitle() {
        setRootTitleWithResource("dlna.title.albumbygenres");
    }

    public BrowseResult browseRoot(String filter, long firstResult, long maxResults, SortCriterion[] orderBy) throws Exception {
        DIDLContent didl = new DIDLContent();
        List<MediaFile> selectedItems = getItems(firstResult, maxResults);
        for (int i = 0; i < selectedItems.size(); i++) {
            MediaFile item = selectedItems.get(i);
            didl.addContainer(createContainer(item, Integer.toString((int) (i + firstResult))));
        }
        return createBrowseResult(didl, (int) didl.getCount(), getItemCount());
    }

    @Override
    public Container createContainer(MediaFile item) {
        if (!isEmpty(item.getMediaType())) {
            MusicAlbum container = new MusicAlbum();
            container.setAlbumArtURIs(new URI[] { getDispatcher().getMediaFileProcessor().createAlbumArtURI(item) });
            if (item.getArtist() != null) {
                container.setArtists(getDispatcher().getAlbumProcessor().getAlbumArtists(item.getArtist()));
            }
            container.setDescription(item.getComment());
            container.setId(UpnpProcessDispatcher.CONTAINER_ID_FOLDER_PREFIX + UpnpProcessDispatcher.OBJECT_ID_SEPARATOR + item.getId());
            container.setTitle(item.getName());
            container.setChildCount(getChildSizeOf(item));
            if (!mediaFileService.isRoot(item)) {
                MediaFile parent = mediaFileService.getParentOf(item);
                if (parent != null) {
                    container.setParentID(String.valueOf(parent.getId()));
                }
            } else {
                container.setParentID(getRootId());
            }
            return container;

        }
        return null;
    }

    private final Container createContainer(MediaFile item, String index) {
        MusicAlbum container = new MusicAlbum();
        if (isEmpty(item.getMediaType())) {
            container.setParentID(getRootId());
            container.setId(getRootId() + UpnpProcessDispatcher.OBJECT_ID_SEPARATOR + index);
            container.setTitle(isDlnaGenreCountVisible() ? item.getName().concat(SPACE).concat(item.getComment()) : item.getName());
            container.setChildCount(isEmpty(item.getComment()) ? 0 : Integer.parseInt(item.getComment()));
        }
        return container;
    }

    @Override
    public int getItemCount() {
        return searchService.getGenresCount(true);
    }

    private final Function<Genre, MediaFile> toMediaFile = (g) -> {
        MediaFile m = new MediaFile();
        m.setId(-1);
        m.setTitle(g.getName());
        if (0 != g.getAlbumCount()) {
            m.setComment(Integer.toString(g.getAlbumCount()));
        }
        m.setGenre(g.getName());
        return m;
    };

    @Override
    public List<MediaFile> getItems(long offset, long maxResults) {
        return searchService.getGenres(true, offset, maxResults).stream().map(toMediaFile).collect(Collectors.toList());
    }

    public MediaFile getItemById(String id) {
        int index = Integer.parseInt(id);
        List<Genre> genres = searchService.getGenres(true);
        if (genres.size() > index) {
            return toMediaFile.apply(genres.get(index));
        }
        return null;
    }

    @Override
    public int getChildSizeOf(MediaFile item) {
        return searchService.getAlbumsByGenres(item.getName(), 0, Integer.MAX_VALUE, getAllMusicFolders()).size();
    }

    @Override
    public List<MediaFile> getChildren(MediaFile item, long offset, long maxResults) {
        if (-1 == item.getId()) {
            return searchService.getAlbumsByGenres(item.getGenre(), (int) offset, (int) maxResults, getAllMusicFolders());
        }
        return mediaFileDao.getSongsForAlbum(item, offset, maxResults);
    }

    public void addChild(DIDLContent didl, MediaFile child) {
        if (isEmpty(child.getMediaType())) {
            didl.addItem(getDispatcher().getMediaFileProcessor().createItem(child));
        } else {
            didl.addContainer(createContainer(child));
        }
    }

}
