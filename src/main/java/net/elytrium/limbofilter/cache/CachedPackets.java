/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.cache;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.Disconnect;
import com.velocitypowered.proxy.protocol.packet.chat.LegacyChat;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChat;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualChunk;
import net.elytrium.limboapi.api.material.Item;
import net.elytrium.limboapi.api.material.VirtualItem;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.PacketFactory;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;

public class CachedPackets {

  private PreparedPacket fallingCheckPackets;
  private List<PreparedPacket> captchaAttemptsPacket;
  private PreparedPacket captchaFailed;
  private PreparedPacket fallingCheckFailed;
  private PreparedPacket timesUp;
  private PreparedPacket resetSlot;
  private PreparedPacket kickClientCheckSettings;
  private PreparedPacket kickClientCheckBrand;
  private PreparedPacket successfulBotFilterChat;
  private PreparedPacket successfulBotFilterDisconnect;
  private PreparedPacket noAbilities;
  private List<PreparedPacket> experience;

  public void createPackets(LimboFactory limboFactory, PacketFactory packetFactory) {
    Settings.MAIN.STRINGS strings = Settings.IMP.MAIN.STRINGS;

    this.captchaAttemptsPacket = this.createCaptchaAttemptsPacket(limboFactory, packetFactory, strings.CHECKING_CAPTCHA_TITLE,
        strings.CHECKING_CAPTCHA_SUBTITLE, strings.CHECKING_CAPTCHA_CHAT, strings.CHECKING_WRONG_CAPTCHA_CHAT);
    this.fallingCheckPackets =
        this.createFallingCheckPackets(limboFactory, packetFactory, strings.CHECKING_TITLE, strings.CHECKING_SUBTITLE, strings.CHECKING_CHAT);
    this.captchaFailed = this.createDisconnectPacket(limboFactory, strings.CAPTCHA_FAILED_KICK);
    this.fallingCheckFailed = this.createDisconnectPacket(limboFactory, strings.FALLING_CHECK_FAILED_KICK);
    this.timesUp = this.createDisconnectPacket(limboFactory, strings.TIMES_UP);

    this.resetSlot = limboFactory.createPreparedPacket().prepare(this.createSetSlotPacket(packetFactory, limboFactory.getItem(Item.AIR), 0, null)).build();

    this.kickClientCheckSettings = this.createDisconnectPacket(limboFactory, strings.CLIENT_SETTINGS_KICK);
    this.kickClientCheckBrand = this.createDisconnectPacket(limboFactory, strings.CLIENT_BRAND_KICK);

    this.successfulBotFilterChat = this.createChatPacket(limboFactory, strings.SUCCESSFUL_CRACKED);
    this.successfulBotFilterDisconnect = this.createDisconnectPacket(limboFactory, strings.SUCCESSFUL_PREMIUM_KICK);

    this.noAbilities = this.createAbilitiesPacket(limboFactory, packetFactory);
    this.experience = this.createExpPackets(limboFactory, packetFactory);
  }

  private List<PreparedPacket> createCaptchaAttemptsPacket(LimboFactory limboFactory, PacketFactory packetFactory,
                                                           String checkingChat, String checkingTitle, String checkingSubtitle, String wrongCaptcha) {
    List<PreparedPacket> packets = new ArrayList<>(Settings.IMP.MAIN.CAPTCHA_ATTEMPTS);

    int lastId = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS - 1;
    for (int i = 0; i < lastId; ++i) {
      packets.add(i, limboFactory.createPreparedPacket()
          .prepare(this.createChatPacket(limboFactory, MessageFormat.format(wrongCaptcha, i)))
          .prepare(
              this.createSetSlotPacket(
                  packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
              ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_16_4
          ).prepare(
              this.createSetSlotPacket(
                  packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, CompoundBinaryTag.builder().put("map", IntBinaryTag.of(0)).build()
              ), ProtocolVersion.MINECRAFT_1_17
          )
          .build()
      );
    }

    packets.add(lastId, limboFactory.createPreparedPacket()
        .prepare(this.createCaptchaFirstAttemptPacket(limboFactory, checkingTitle, checkingSubtitle, checkingChat))
        .prepare(
            this.createSetSlotPacket(
                packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
            ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_16_4
        ).prepare(
            this.createSetSlotPacket(
                packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, CompoundBinaryTag.builder().put("map", IntBinaryTag.of(0)).build()
            ), ProtocolVersion.MINECRAFT_1_17
        )
        .build());

    return packets;
  }

  private PreparedPacket createCaptchaFirstAttemptPacket(LimboFactory factory, String checkingTitle, String checkingSubtitle, String checkingChat) {
    PreparedPacket preparedPacket = factory.createPreparedPacket()
        .prepare(this.createChatPacket(factory, MessageFormat.format(checkingChat, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS)));

    if (!checkingTitle.isEmpty() && !checkingSubtitle.isEmpty()) {
      preparedPacket.prepare(
          this.createTitlePacket(
              factory,
              MessageFormat.format(checkingTitle, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS),
              MessageFormat.format(checkingSubtitle, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS)
          )
      );
    }

    return preparedPacket.build();
  }

  private PreparedPacket createFallingCheckPackets(LimboFactory limboFactory, PacketFactory packetFactory,
                                                   String checkingTitle, String checkingSubtitle, String checkingChat) {
    Settings.MAIN.FALLING_COORDS fallingCoords = Settings.IMP.MAIN.FALLING_COORDS;

    Settings.MAIN.COORDS coords = Settings.IMP.MAIN.COORDS;
    PreparedPacket preparedPacket = limboFactory.createPreparedPacket().prepare(
        this.createPlayerPosAndLook(
            packetFactory,
            fallingCoords.X, fallingCoords.Y, fallingCoords.Z,
            (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH
        )
    ).prepare(this.createChunkData(
        packetFactory, limboFactory.createVirtualChunk(fallingCoords.X >> 4, fallingCoords.Z >> 4)
    )).prepare(this.createUpdateViewPosition(packetFactory, fallingCoords.X, fallingCoords.Z), ProtocolVersion.MINECRAFT_1_14);

    if (!Settings.IMP.MAIN.STRINGS.CHECKING_TITLE.isEmpty() && !Settings.IMP.MAIN.STRINGS.CHECKING_SUBTITLE.isEmpty()) {
      preparedPacket.prepare(this.createTitlePacket(limboFactory, checkingTitle, checkingSubtitle), ProtocolVersion.MINECRAFT_1_8);
    }

    if (!checkingChat.isEmpty()) {
      preparedPacket.prepare(this.createChatPacket(limboFactory, checkingChat));
    }

    return preparedPacket.build();
  }

  private MinecraftPacket createChunkData(PacketFactory factory, VirtualChunk chunk) {
    chunk.setSkyLight(chunk.getX() & 15, 256, chunk.getZ() & 15, (byte) 1);
    return (MinecraftPacket)
        factory.createChunkDataPacket(chunk.getFullChunkSnapshot(), true, Dimension.valueOf(Settings.IMP.MAIN.BOTFILTER_DIMENSION).getMaxSections());
  }

  private MinecraftPacket createPlayerPosAndLook(PacketFactory factory, double x, double y, double z, float yaw, float pitch) {
    return (MinecraftPacket) factory.createPositionRotationPacket(x, y, z, yaw, pitch, false, 44, true);
  }

  private MinecraftPacket createUpdateViewPosition(PacketFactory factory, int x, int z) {
    return (MinecraftPacket) factory.createUpdateViewPositionPacket(x >> 4, z >> 4);
  }

  private PreparedPacket createAbilitiesPacket(LimboFactory limboFactory, PacketFactory packetFactory) {
    return limboFactory.createPreparedPacket().prepare(packetFactory.createPlayerAbilitiesPacket((byte) 6, 0f, 0f)).build();
  }

  private List<PreparedPacket> createExpPackets(LimboFactory limboFactory, PacketFactory packetFactory) {
    List<PreparedPacket> packets = new ArrayList<>();
    long ticks = Settings.IMP.MAIN.FALLING_CHECK_TICKS;
    float expInterval = 0.01F;
    for (int i = 0; i < ticks; ++i) {
      int percentage = (int) (i * 100 / ticks);
      packets.add(
          limboFactory.createPreparedPacket().prepare(packetFactory.createSetExperiencePacket(percentage * expInterval, percentage, 0)).build()
      );
    }

    return packets;
  }

  private MinecraftPacket createSetSlotPacket(PacketFactory packetFactory, VirtualItem item, int count, CompoundBinaryTag nbt) {
    return (MinecraftPacket) packetFactory.createSetSlotPacket(0, 36, item, count, 0, nbt);
  }

  public PreparedPacket createChatPacket(LimboFactory factory, String text) {
    return factory.createPreparedPacket()
        .prepare(new LegacyChat(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINIMUM_VERSION).serialize(
                LimboFilter.getSerializer().deserialize(text)
            ), LegacyChat.CHAT_TYPE, null
        ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_15_2)
        .prepare(new LegacyChat(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_16).serialize(
                LimboFilter.getSerializer().deserialize(text)
            ), LegacyChat.CHAT_TYPE, null
        ), ProtocolVersion.MINECRAFT_1_16, ProtocolVersion.MINECRAFT_1_18_2)
        .prepare(new SystemChat(
            LimboFilter.getSerializer().deserialize(text), 1
        ), ProtocolVersion.MINECRAFT_1_19).build();
  }

  private PreparedPacket createDisconnectPacket(LimboFactory factory, String message) {
    return factory.createPreparedPacket().prepare(version -> Disconnect.create(LimboFilter.getSerializer().deserialize(message), version)).build();
  }

  public PreparedPacket createTitlePacket(LimboFactory factory, String title, String subtitle) {
    PreparedPacket preparedPacket = factory.createPreparedPacket();

    preparedPacket.prepare(version -> {
      GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TITLE, version);
      packet.setComponent(ProtocolUtils.getJsonChatSerializer(version).serialize(LimboFilter.getSerializer().deserialize(title)));
      return packet;
    }, ProtocolVersion.MINECRAFT_1_8);

    if (!subtitle.isEmpty()) {
      preparedPacket.prepare(version -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_SUBTITLE, version);
        packet.setComponent(ProtocolUtils.getJsonChatSerializer(version).serialize(LimboFilter.getSerializer().deserialize(subtitle)));
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }

    if (!subtitle.isEmpty() && !title.isEmpty()) {
      preparedPacket.prepare(version -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TIMES, version);
        packet.setFadeIn(10);
        packet.setStay(70);
        packet.setFadeOut(20);
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }

    return preparedPacket.build();
  }

  public PreparedPacket getCaptchaFailed() {
    return this.captchaFailed;
  }

  public PreparedPacket getFallingCheckFailed() {
    return this.fallingCheckFailed;
  }

  public PreparedPacket getTimesUp() {
    return this.timesUp;
  }

  public PreparedPacket getResetSlot() {
    return this.resetSlot;
  }

  public PreparedPacket getKickClientCheckSettings() {
    return this.kickClientCheckSettings;
  }

  public PreparedPacket getKickClientCheckBrand() {
    return this.kickClientCheckBrand;
  }

  public PreparedPacket getSuccessfulBotFilterChat() {
    return this.successfulBotFilterChat;
  }

  public PreparedPacket getSuccessfulBotFilterDisconnect() {
    return this.successfulBotFilterDisconnect;
  }

  public PreparedPacket getNoAbilities() {
    return this.noAbilities;
  }

  public List<PreparedPacket> getExperience() {
    return this.experience;
  }

  public PreparedPacket getFallingCheckPackets() {
    return this.fallingCheckPackets;
  }

  public PreparedPacket getCaptchaAttemptsPacket(int attempt) {
    return this.captchaAttemptsPacket.get(attempt);
  }
}
