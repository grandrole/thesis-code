Ext.define("MobiPrint.controller.Orders", {
    extend: "Ext.app.Controller",

    config: {
        refs: {
            currentOrderDetailView: "#current-order-detail",
            currentOrderNavigationView: "#currentorder-navigationview",
            ordersListLabel: "#orders-list-label",
            ordersListNavigationView: "#orderslist-navigationview",
            orderSubmissionView: "#order-submission",
            showOrderSubmissionButton: "#show-submit-order-button",
            submitOrderButton: "#submit-order-button",
            orderSubmissionView: "#order-submission",
        },
        control: {
            "#current-order-detail": {
                show: "onShowOrderDetail"
            },
            "#orders-list": {
                itemtap: "onDiscloseOrder"
            },
            "#show-submit-order-button": {
                tap: "onShowSubmitOrder"
            },
            "#submit-order-button": {
                tap: "onSubmitOrder"
            },
        }
    },

    autoDownloadThumbnails: function(lastNOrders) {
        var ordersStore = Ext.getStore("Orders")
        var orders = ordersStore.getData().items
        var hadCurrentOrder = false

        for(var n = Math.max(0, orders.length - lastNOrders); n < orders.length; ++n)
        {
            var order = orders[n].data

            if(!order.submissionDate)
                hadCurrentOrder = true

            for(var i = 0; i < order.pictureIds.length; ++i)
                this.startThumbnailDownload(order.pictureIds[i])
        }

        if(!hadCurrentOrder)
            for(var n = 0; n < orders.length - lastNOrders; ++n)
            {
                var order = orders[n].data

                if(!order.submissionDate)
                {
                    for(var i = 0; i < order.pictureIds.length; ++i)
                        this.startThumbnailDownload(order.pictureIds[i])

                    break
                }
            }
    },

    constructor: function(config) {
        this.callParent([config])

        this.downloadingThumbnails = {}
    },

    getUploadingPictures: function() {
        var uploadingPictures = MobiPrint.controller.PictureFolders.getUploadingPictures()
        var ret = {}

        // Must copy the object in order to pass it to the view or else it cannot determine when to update the view
        // if something changed
        for(var key in uploadingPictures)
            ret[key] = uploadingPictures[key]

        return ret
    },

    launch: function() {
        console.log("Controller: Orders")

        var _this = this

        var app = this.getInitialConfig("application")
        app.addListener("picture-upload-started", function() {
            _this.updateCurrentOrderDetail()
        })
        app.addListener("picture-upload-finished", function() {
            Ext.getStore("Orders").updateOrdersList()
        })

        Ext.getStore("Orders").addListener("refresh", this.onOrdersListRefresh, this)

        this.onOrdersListRefresh()
        setInterval(function() { Ext.getStore("Orders").updateOrdersList() }, 60000)
        setTimeout(function() { Ext.getStore("Orders").updateOrdersList() }, 1000)

        // Auto-download thumbnails of current order and last order
        this.autoDownloadThumbnails(2)
    },

    onDiscloseOrder: function(list, index, target, record) {
        this.showOrderDetail(record.data)
    },

    onOrdersListRefresh: function() {
        var ordersStore = Ext.getStore("Orders")

        // Automatically cache thumbnails of last 5 orders
        this.autoDownloadThumbnails(5)

        var count = ordersStore.getOldOrdersCount()
        this.getOrdersListLabel().setHtml(Ext.String.format(_("NUM_OLD_ORDERS_FMT").toString(), count))

        this.updateCurrentOrderDetail()
    },

    onShowOrderDetail: function() {
        this.getShowOrderSubmissionButton().show()
    },

    onShowSubmitOrder: function() {
        var currentOrder = Ext.getStore("Orders").findRecord("submissionDate", null)

        if(!currentOrder || currentOrder.get("pictureIds").length == 0)
        {
            navigator.notification.alert(_("NO_PICTURES_SELECTED"))
            return
        }

        this.getShowOrderSubmissionButton().hide()
        this.getCurrentOrderNavigationView().push({xtype: "mobiprint-ordersubmission"})
        this.getOrderSubmissionView().setNumPictures(currentOrder.get("pictureIds").length)
    },

    onSubmitOrder: function() {
        Ext.getStore("Locations").retrieve("test location")
    },

    showOrderDetail: function(order) {
        var orderDetailView = Ext.create("MobiPrint.view.OrderDetail")

        var viewData = {order: order}

        if(!order.submissionDate)
            viewData.uploadingPictures = this.getUploadingPictures()

        orderDetailView.setData(viewData)

        this.getOrdersListNavigationView().push(orderDetailView)
    },

    startThumbnailDownload: function(pictureId) {
        if(this.downloadingThumbnails[pictureId] == true)
            return

        this.downloadingThumbnails[pictureId] = true

        try
        {
            console.log("Automatically downloading thumbnail " + pictureId)

            if(THUMBNAIL_SIZE == null)
                THUMBNAIL_SIZE = Math.max(5, Math.min(500, Math.floor(window.innerWidth * 2 / 3)))

            Ext.Ajax.request({
                url: WEB_SERVICE_BASE_URI + "picture/" + pictureId + "/thumbnail/?size=" + THUMBNAIL_SIZE,
                timeout: 20000,
                success: function(response) {
                    console.log("Successfully downloaded thumbnail " + pictureId)
                },
                callback: function() {
                    delete this.downloadingThumbnails[pictureId]
                },
                scope: this
            })
        }
        catch(e)
        {
            delete this.downloadingThumbnails[pictureId]
            throw e
        }
    },

    updateCurrentOrderDetail: function() {
        var view = this.getCurrentOrderDetailView()
        var currentOrder = Ext.getStore("Orders").findRecord("submissionDate", null)

        if(currentOrder === null)
        {
            currentOrder = {
                pictureIds: [],
                storeId: null,
                submissionDate: null
            }
        }
        else
            currentOrder = currentOrder.data

        view.setData({order: currentOrder,
                      uploadingPictures: this.getUploadingPictures()})
    }
})
