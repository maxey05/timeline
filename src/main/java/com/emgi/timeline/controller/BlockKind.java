package com.emgi.timeline.controller;

public enum BlockKind
{
    TEXT("Text", "text"),
    LINK("Link", "link"),
    IMAGE("Image", "image");

    private final String displayName;
    private final String addressWord;

    BlockKind(String displayName, String addressWord)
    {
        this.displayName = displayName;
        this.addressWord = addressWord;
    }

    public String displayName()
    {
        return displayName;
    }

    public String addressWord()
    {
        return addressWord;
    }
}
