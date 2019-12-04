/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.SocksCmdResponseDecoder.State;
import io.netty.util.NetUtil;

import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksCmdResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksCmdResponseDecoder extends ReplayingDecoder<State> {

    private SocksCmdStatus cmdStatus;
    private SocksAddressType addressType;

    public SocksCmdResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readByte() != SocksProtocolVersion.SOCKS5.byteValue()) {
                    out.add(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                    checkpoint(State.DONE);
                    return;
                }
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdStatus = SocksCmdStatus.valueOf(byteBuf.readByte());
                byteBuf.skipBytes(1); // reserved
                addressType = SocksAddressType.valueOf(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        String host = NetUtil.intToIpAddress(byteBuf.readInt());
                        int port = byteBuf.readUnsignedShort();
                        out.add(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        checkpoint(State.DONE);
                        return;
                    }
                    case DOMAIN: {
                        int fieldLength = byteBuf.readByte();
                        String host = SocksCommonUtils.readUsAscii(byteBuf, fieldLength);
                        int port = byteBuf.readUnsignedShort();
                        out.add(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        checkpoint(State.DONE);
                        return;
                    }
                    case IPv6: {
                        byte[] bytes = new byte[16];
                        byteBuf.readBytes(bytes);
                        String host = SocksCommonUtils.ipv6toStr(bytes);
                        int port = byteBuf.readUnsignedShort();
                        out.add(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        checkpoint(State.DONE);
                        return;
                    }
                    case UNKNOWN: {
                        out.add(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                        checkpoint(State.DONE);
                        return;
                    }
                    default: {
                        throw new Error();
                    }
                }
            }
            case DONE:
                ctx.pipeline().remove(this);
                return;
            default: {
                throw new Error();
            }
        }
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS,
        DONE
    }
}
