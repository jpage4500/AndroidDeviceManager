package com.jpage4500.devicemanager.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GithubRelease {
    @SerializedName("tag_name")
    public String tagName;
    public String name;
    public String body;
    @SerializedName("html_url")
    public String htmlUrl;
    public boolean prerelease;
    public List<GithubAsset> assets;

    public static class GithubAsset {
        public String name;
        @SerializedName("browser_download_url")
        public String browserDownloadUrl;
    }
}

/*
  {
    "url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases/158232416",
    "assets_url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases/158232416/assets",
    "upload_url": "https://uploads.github.com/repos/jpage4500/AndroidDeviceManager/releases/158232416/assets{?name,label}",
    "html_url": "https://github.com/jpage4500/AndroidDeviceManager/releases/tag/2024.05.30",
    "id": 158232416,
    "author": {
      "login": "jpage4500",
      "id": 1895553,
      "node_id": "MDQ6VXNlcjE4OTU1NTM=",
      "avatar_url": "https://avatars.githubusercontent.com/u/1895553?v=4",
      "gravatar_id": "",
      "url": "https://api.github.com/users/jpage4500",
      "html_url": "https://github.com/jpage4500",
      "followers_url": "https://api.github.com/users/jpage4500/followers",
      "following_url": "https://api.github.com/users/jpage4500/following{/other_user}",
      "gists_url": "https://api.github.com/users/jpage4500/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/jpage4500/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/jpage4500/subscriptions",
      "organizations_url": "https://api.github.com/users/jpage4500/orgs",
      "repos_url": "https://api.github.com/users/jpage4500/repos",
      "events_url": "https://api.github.com/users/jpage4500/events{/privacy}",
      "received_events_url": "https://api.github.com/users/jpage4500/received_events",
      "type": "User",
      "site_admin": false
    },
    "node_id": "RE_kwDOG-D0Ac4Jbm9g",
    "tag_name": "2024.05.30",
    "target_commitish": "develop",
    "name": "2024.05.30",
    "draft": false,
    "prerelease": false,
    "created_at": "2024-05-30T18:56:36Z",
    "published_at": "2024-05-30T18:58:11Z",
    "assets": [
      {
        "url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases/assets/171008597",
        "id": 171008597,
        "node_id": "RA_kwDOG-D0Ac4KMWJV",
        "name": "AndroidDeviceManager-24.05.30.2018-OSX.zip",
        "label": null,
        "uploader": {
          "login": "jpage4500",
          "id": 1895553,
          "node_id": "MDQ6VXNlcjE4OTU1NTM=",
          "avatar_url": "https://avatars.githubusercontent.com/u/1895553?v=4",
          "gravatar_id": "",
          "url": "https://api.github.com/users/jpage4500",
          "html_url": "https://github.com/jpage4500",
          "followers_url": "https://api.github.com/users/jpage4500/followers",
          "following_url": "https://api.github.com/users/jpage4500/following{/other_user}",
          "gists_url": "https://api.github.com/users/jpage4500/gists{/gist_id}",
          "starred_url": "https://api.github.com/users/jpage4500/starred{/owner}{/repo}",
          "subscriptions_url": "https://api.github.com/users/jpage4500/subscriptions",
          "organizations_url": "https://api.github.com/users/jpage4500/orgs",
          "repos_url": "https://api.github.com/users/jpage4500/repos",
          "events_url": "https://api.github.com/users/jpage4500/events{/privacy}",
          "received_events_url": "https://api.github.com/users/jpage4500/received_events",
          "type": "User",
          "site_admin": false
        },
        "content_type": "application/zip",
        "state": "uploaded",
        "size": 5301165,
        "download_count": 7,
        "created_at": "2024-05-30T20:21:31Z",
        "updated_at": "2024-05-30T20:21:32Z",
        "browser_download_url": "https://github.com/jpage4500/AndroidDeviceManager/releases/download/2024.05.30/AndroidDeviceManager-24.05.30.2018-OSX.zip"
      },
      {
        "url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases/assets/171008608",
        "id": 171008608,
        "node_id": "RA_kwDOG-D0Ac4KMWJg",
        "name": "AndroidDeviceManager.jar",
        "label": null,
        "uploader": {
          "login": "jpage4500",
          "id": 1895553,
          "node_id": "MDQ6VXNlcjE4OTU1NTM=",
          "avatar_url": "https://avatars.githubusercontent.com/u/1895553?v=4",
          "gravatar_id": "",
          "url": "https://api.github.com/users/jpage4500",
          "html_url": "https://github.com/jpage4500",
          "followers_url": "https://api.github.com/users/jpage4500/followers",
          "following_url": "https://api.github.com/users/jpage4500/following{/other_user}",
          "gists_url": "https://api.github.com/users/jpage4500/gists{/gist_id}",
          "starred_url": "https://api.github.com/users/jpage4500/starred{/owner}{/repo}",
          "subscriptions_url": "https://api.github.com/users/jpage4500/subscriptions",
          "organizations_url": "https://api.github.com/users/jpage4500/orgs",
          "repos_url": "https://api.github.com/users/jpage4500/repos",
          "events_url": "https://api.github.com/users/jpage4500/events{/privacy}",
          "received_events_url": "https://api.github.com/users/jpage4500/received_events",
          "type": "User",
          "site_admin": false
        },
        "content_type": "application/java-archive",
        "state": "uploaded",
        "size": 2927496,
        "download_count": 3,
        "created_at": "2024-05-30T20:21:41Z",
        "updated_at": "2024-05-30T20:21:42Z",
        "browser_download_url": "https://github.com/jpage4500/AndroidDeviceManager/releases/download/2024.05.30/AndroidDeviceManager.jar"
      }
    ],
    "tarball_url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/tarball/2024.05.30",
    "zipball_url": "https://api.github.com/repos/jpage4500/AndroidDeviceManager/zipball/2024.05.30",
    "body": "## What's Changed\r\n* - continue moving actions to using JADB by @jpage4500 in https://github.com/jpage4500/AndroidDeviceManager/pull/4\r\n\r\n\r\n**Full Changelog**: https://github.com/jpage4500/AndroidDeviceManager/compare/2024.05.24...2024.05.30",
    "mentions_count": 1
  },

 */