/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class MessageInfo extends Entity {
    private final Object obj;

    public MessageInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Entity messageDataInfo() throws Exception{
        return super.getFieldAsEntity("A00");
    }

    public String getMessageType() throws Exception {
        Entity messageTypeDetailEntity = super.getFieldAsEntity("A01");
        return (String) messageTypeDetailEntity.getField("A00");
    }

    public MediaData getVisualMedia() throws Exception {
        Entity data = messageDataInfo();
        Object media;
        if ("raven_media".equals(getMessageType())) {
            Object raven = data.getField("A0L");
            media = raven == null ? null : new Entity(raven).getField("A05");
        } else if ("media".equals(getMessageType())) {
            media = data.getField("A0X");
        } else {
            return null;
        }
        return media == null ? null : new MediaData(media);
    }

    public MediaData getAudioMedia() throws Exception {
        Entity messageTypeDetailEntity = this.messageDataInfo();
        Entity audioDataEntity = messageTypeDetailEntity.getFieldAsEntity("A0O");
        if(audioDataEntity!=null){
            Object mediaData = audioDataEntity.getField("A02");
            if(mediaData!=null)
                return new MediaData(mediaData);
        }
        return null;

    }
}
