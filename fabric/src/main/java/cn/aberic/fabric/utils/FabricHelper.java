package cn.aberic.fabric.utils;

import cn.aberic.fabric.module.bean.dto.*;
import cn.aberic.fabric.module.mapper.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.fabric.sdk.aberic.FabricManager;
import org.hyperledger.fabric.sdk.aberic.OrgManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：
 *
 * @author : Aberic 【2018/6/4 10:46】
 */
public class FabricHelper {

    private Logger logger = LogManager.getLogger(FabricHelper.class);

    /** 当前正在运行的智能合约Id */
    private int chainCodeId;

    private static FabricHelper instance;

    private final Map<Integer, FabricManager> fabricManagerMap;

    public static FabricHelper obtain() {
        if (null == instance) {
            synchronized (FabricHelper.class) {
                if (null == instance) {
                    instance = new FabricHelper();
                }
            }
        }
        return instance;
    }

    private FabricHelper() {
        fabricManagerMap = new HashMap<>();
    }

    public FabricManager get(OrgMapper orgMapper, ChannelMapper channelMapper, ChainCodeMapper chainCodeMapper,
                             OrdererMapper ordererMapper, PeerMapper peerMapper) throws Exception {
        return get(orgMapper, channelMapper, chainCodeMapper, ordererMapper, peerMapper, -1);
    }

    public FabricManager get(OrgMapper orgMapper, ChannelMapper channelMapper, ChainCodeMapper chainCodeMapper,
                             OrdererMapper ordererMapper, PeerMapper peerMapper, int chainCodeId) throws Exception {
        if (chainCodeId == -1) {
            chainCodeId = this.chainCodeId;
        } else {
            this.chainCodeId = chainCodeId;
        }

        // 尝试从缓存中获取fabricManager
        FabricManager fabricManager = fabricManagerMap.get(chainCodeId);
        if (null == fabricManager) { // 如果不存在fabricManager则尝试新建一个并放入缓存
            synchronized (fabricManagerMap) {
                ChainCodeDTO chainCode = chainCodeMapper.get(chainCodeId);
                logger.debug(String.format("chaincode = %s", chainCode.toString()));
                ChannelDTO channel = channelMapper.get(chainCode.getChannelId());
                logger.debug(String.format("channel = %s", channel.toString()));
                PeerDTO peer = peerMapper.get(channel.getPeerId());
                logger.debug(String.format("peer = %s", peer.toString()));
                int orgId = peer.getOrgId();
                List<PeerDTO> peers = peerMapper.list(orgId);
                List<OrdererDTO> orderers = ordererMapper.list(orgId);
                OrgDTO org = orgMapper.get(orgId);
                logger.debug(String.format("org = %s", org.toString()));
                if (orderers.size() != 0 && peers.size() != 0) {
                    fabricManager = createFabricManager(org, channel, chainCode, orderers, peers);
                    fabricManagerMap.put(chainCodeId, fabricManager);
                }
            }
        }
        return fabricManager;
    }


    private FabricManager createFabricManager(OrgDTO org, ChannelDTO channel, ChainCodeDTO chainCode, List<OrdererDTO> orderers, List<PeerDTO> peers) throws Exception {
        OrgManager orgManager = new OrgManager();
        orgManager
                .init(chainCodeId, org.isTls())
                .setUser(org.getUsername(), org.getCryptoConfigDir())
                .setPeers(org.getName(), org.getMspId(), org.getDomainName())
                .setOrderers(org.getOrdererDomainName())
                .setChannel(channel.getName())
                .setChainCode(chainCode.getName(), chainCode.getPath(), chainCode.getVersion(), chainCode.getProposalWaitTime(), chainCode.getInvokeWaitTime())
                .setBlockListener(map -> {
                    logger.debug(map.get("code"));
                    logger.debug(map.get("data"));
                });
        for (OrdererDTO orderer : orderers) {
            orgManager.addOrderer(orderer.getName(), orderer.getLocation());
        }
        for (PeerDTO peer : peers) {
            orgManager.addPeer(peer.getName(), peer.getEventHubName(), peer.getLocation(), peer.getEventHubLocation(), peer.isEventListener());
        }
        orgManager.add();
        return orgManager.use(chainCodeId);
    }

}
